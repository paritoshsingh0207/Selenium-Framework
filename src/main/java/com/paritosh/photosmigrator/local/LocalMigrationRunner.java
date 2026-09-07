package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.DestinationAuthenticator.AuthenticatedDestination;
import com.paritosh.photosmigrator.local.MediaTransferClient.CreatedMediaItem;
import com.paritosh.photosmigrator.local.MediaTransferClient.DestinationMediaItem;
import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;

import java.nio.file.Path;
import java.util.List;

public class LocalMigrationRunner {
    private static final String UPLOAD_TOKEN_PREFIX = "upload-token:";

    private final DestinationAuthenticator authenticator;
    private final MediaTransferClient transferClient;
    private final int maxAttempts;

    public LocalMigrationRunner(DestinationAuthenticator authenticator, MediaTransferClient transferClient) {
        this(authenticator, transferClient, 5);
    }

    LocalMigrationRunner(DestinationAuthenticator authenticator, MediaTransferClient transferClient, int maxAttempts) {
        this.authenticator = authenticator;
        this.transferClient = transferClient;
        this.maxAttempts = maxAttempts;
    }

    public LocalMigrationRunResult run(Path ledgerPath, int maxItems) {
        if (maxItems <= 0) throw new IllegalArgumentException("maxItems must be greater than zero");
        int processed = 0;
        int uploaded = 0;
        int verified = 0;
        int waiting = 0;
        int failed = 0;
        int reconciliation = 0;

        try (LocalMigrationLedger ledger = LocalMigrationLedger.open(ledgerPath)) {
            markUncertainCreatesForReconciliation(ledger);
            List<String> destinations = ledger.destinationAccounts();

            for (String expectedDestination : destinations) {
                if (processed >= maxItems) break;
                List<LedgerMigrationRow> work = eligibleRows(ledger, expectedDestination,
                        maxItems - processed);
                if (work.isEmpty()) continue;

                AuthenticatedDestination authenticated = authenticator.authenticate(expectedDestination);
                if (!expectedDestination.equalsIgnoreCase(authenticated.email())) {
                    throw new IllegalArgumentException("Wrong Google account connected. Expected "
                            + expectedDestination + " but got " + authenticated.email());
                }
                AccessTokenProvider tokens = authenticated.tokens();

                for (LedgerMigrationRow row : work) {
                    if (processed >= maxItems) break;
                    processed++;
                    try {
                        ItemResult result = processRow(ledger, tokens, row);
                        uploaded += result.uploaded() ? 1 : 0;
                        verified += result.verified() ? 1 : 0;
                        waiting += result.waiting() ? 1 : 0;
                        reconciliation += result.reconciliationRequired() ? 1 : 0;
                    } catch (RuntimeException e) {
                        failed++;
                        handleFailure(ledger, row, e);
                    }
                }
            }
            ledger.checkpoint();
            reconciliation += Math.toIntExact(ledger.countStatus("NEEDS_RECONCILIATION"));
        }

        return new LocalMigrationRunResult(processed, uploaded, verified, waiting, failed, reconciliation);
    }

    private void markUncertainCreatesForReconciliation(LocalMigrationLedger ledger) {
        for (LedgerMigrationRow row : ledger.rows()) {
            if ("CREATING_MEDIA".equals(row.status())) {
                ledger.update(row.id(), "NEEDS_RECONCILIATION", row.destinationRef(),
                        "Previous run stopped while Google media creation may have been in flight. "
                                + "Automatic re-upload is blocked to prevent a duplicate.",
                        false, true);
            }
        }
    }

    private List<LedgerMigrationRow> eligibleRows(LocalMigrationLedger ledger, String destination, int limit) {
        return ledger.rows().stream()
                .filter(row -> destination.equalsIgnoreCase(row.destinationAccount()))
                .filter(row -> !row.duplicate())
                .filter(this::isRunnable)
                .limit(limit)
                .toList();
    }

    private boolean isRunnable(LedgerMigrationRow row) {
        return switch (row.status()) {
            case "PLANNED", "FAILED_RETRYABLE", "UPLOADING", "UPLOADED_BYTES",
                    "UPLOADED", "WAITING_DESTINATION_READY" -> true;
            default -> false;
        };
    }

    private ItemResult processRow(LocalMigrationLedger ledger, AccessTokenProvider tokens, LedgerMigrationRow row) {
        String destinationRef = row.destinationRef() == null ? "" : row.destinationRef();

        if (isDestinationMediaId(destinationRef)) {
            return verifyExisting(ledger, tokens, row, destinationRef);
        }

        if ("UPLOADED_BYTES".equals(row.status()) && destinationRef.startsWith(UPLOAD_TOKEN_PREFIX)) {
            return createAndVerify(ledger, tokens, row, destinationRef.substring(UPLOAD_TOKEN_PREFIX.length()), false);
        }

        ledger.update(row.id(), "UPLOADING", "", "", true, true);
        String uploadToken = transferClient.uploadBytes(tokens, row.toTakeoutMediaItem());
        String tokenRef = UPLOAD_TOKEN_PREFIX + uploadToken;
        ledger.update(row.id(), "UPLOADED_BYTES", tokenRef, "", false, true);
        return createAndVerify(ledger, tokens, row, uploadToken, true);
    }

    private ItemResult createAndVerify(LocalMigrationLedger ledger,
                                       AccessTokenProvider tokens,
                                       LedgerMigrationRow row,
                                       String uploadToken,
                                       boolean bytesUploadedThisRun) {
        String tokenRef = UPLOAD_TOKEN_PREFIX + uploadToken;
        ledger.update(row.id(), "CREATING_MEDIA", tokenRef, "", false, true);
        CreatedMediaItem created = transferClient.createMediaItem(tokens, uploadToken, row.fileName());
        ledger.update(row.id(), "UPLOADED", created.id(), "", false, true);

        if (isVideo(row.mimeType()) && !isReady(created.videoStatus())) {
            ledger.update(row.id(), "WAITING_DESTINATION_READY", created.id(),
                    "Destination video is still processing", false, true);
            return new ItemResult(true, false, true, false);
        }
        ItemResult verified = verifyExisting(ledger, tokens, row, created.id());
        return new ItemResult(true || bytesUploadedThisRun, verified.verified(), verified.waiting(), false);
    }

    private ItemResult verifyExisting(LocalMigrationLedger ledger,
                                      AccessTokenProvider tokens,
                                      LedgerMigrationRow row,
                                      String mediaItemId) {
        DestinationMediaItem destination = transferClient.verify(tokens, mediaItemId);
        if (destination.id() == null || destination.id().isBlank()) {
            throw new IllegalStateException("Google Photos verification returned an empty media ID");
        }
        if (!mimeCompatible(row.mimeType(), destination.mimeType())) {
            throw new IllegalStateException("Destination MIME mismatch. Source=" + row.mimeType()
                    + ", destination=" + destination.mimeType());
        }
        if (isVideo(row.mimeType()) && !isReady(destination.videoStatus())) {
            ledger.update(row.id(), "WAITING_DESTINATION_READY", mediaItemId,
                    "Destination video status=" + destination.videoStatus(), false, true);
            return new ItemResult(false, false, true, false);
        }
        ledger.update(row.id(), "VERIFIED", mediaItemId, "", false, true);
        return new ItemResult(false, true, false, false);
    }

    private void handleFailure(LocalMigrationLedger ledger, LedgerMigrationRow originalRow, RuntimeException error) {
        LedgerMigrationRow current = ledger.rows().stream()
                .filter(row -> row.id().equals(originalRow.id()))
                .findFirst()
                .orElse(originalRow);

        if ("CREATING_MEDIA".equals(current.status())) {
            ledger.update(current.id(), "NEEDS_RECONCILIATION", current.destinationRef(),
                    "Media creation outcome is uncertain: " + safeMessage(error), false, true);
            return;
        }

        boolean permanent = current.attempts() >= maxAttempts || sourceProblem(error);
        ledger.update(current.id(), permanent ? "FAILED_FINAL" : "FAILED_RETRYABLE",
                current.destinationRef(), safeMessage(error),
                isDestinationMediaId(current.destinationRef()), true);
    }

    private boolean sourceProblem(RuntimeException error) {
        String message = safeMessage(error).toLowerCase();
        return message.contains("takeout zip entry no longer exists")
                || message.contains("takeout path does not exist")
                || message.contains("media ended early")
                || message.contains("mime type is required");
    }

    private boolean isDestinationMediaId(String ref) {
        return ref != null && !ref.isBlank() && !ref.startsWith(UPLOAD_TOKEN_PREFIX);
    }

    private boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    private boolean isReady(String videoStatus) {
        return videoStatus != null && "READY".equalsIgnoreCase(videoStatus);
    }

    private boolean mimeCompatible(String sourceMime, String destinationMime) {
        if (destinationMime == null || destinationMime.isBlank()) return false;
        if (sourceMime == null || sourceMime.isBlank()) return false;
        if (sourceMime.equalsIgnoreCase(destinationMime)) return true;
        return sourceMime.startsWith("image/") && destinationMime.startsWith("image/")
                || sourceMime.startsWith("video/") && destinationMime.startsWith("video/");
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record ItemResult(boolean uploaded, boolean verified, boolean waiting,
                              boolean reconciliationRequired) { }
}
