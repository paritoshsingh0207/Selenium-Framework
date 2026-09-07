package com.paritosh.photosmigrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paritosh.photosmigrator.google.LibraryUploadClient;
import com.paritosh.photosmigrator.google.PickerApiClient;
import com.paritosh.photosmigrator.model.*;
import com.paritosh.photosmigrator.oauth.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransferService {
    private final GoogleOAuthService oauth;
    private final PickerService pickerService;
    private final PickerApiClient pickerApi;
    private final LibraryUploadClient libraryApi;
    private final MigrationLedgerService ledger;
    private final int maxAttempts;

    public TransferService(
            GoogleOAuthService oauth,
            PickerService pickerService,
            PickerApiClient pickerApi,
            LibraryUploadClient libraryApi,
            MigrationLedgerService ledger,
            @Value("${app.transfer.max-attempts:3}") int maxAttempts) {
        this.oauth = oauth;
        this.pickerService = pickerService;
        this.pickerApi = pickerApi;
        this.libraryApi = libraryApi;
        this.ledger = ledger;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public TransferRunResult transfer(String migrationId, String sessionId, int maxItems) {
        JsonNode session = pickerService.getSession(sessionId);
        if (!session.path("mediaItemsSet").asBoolean(false)) {
            throw new IllegalStateException("The Google Photos Picker session is not complete yet");
        }
        List<PickedMediaItem> selected = pickerService.listAll(sessionId);
        ledger.initialize(migrationId);
        Map<String, MigrationItem> existing = ledger.items(migrationId).stream()
                .filter(item -> item.sourceRef() != null && !item.sourceRef().isBlank())
                .collect(Collectors.toMap(MigrationItem::sourceRef, Function.identity(), (left, right) -> right));

        String sourceToken = oauth.accessToken(AccountRole.SOURCE);
        String destinationToken = oauth.accessToken(AccountRole.DESTINATION);
        int limit = maxItems <= 0 ? selected.size() : Math.min(maxItems, selected.size());
        int processed = 0, verified = 0, skipped = 0, failed = 0;

        for (PickedMediaItem picked : selected) {
            if (processed >= limit) break;
            MigrationItem previous = existing.get(picked.id());
            if (previous != null && (previous.status() == MigrationStatus.VERIFIED || previous.status() == MigrationStatus.SKIPPED_DUPLICATE)) {
                skipped++;
                continue;
            }
            int attempts = previous == null ? 1 : previous.attempts() + 1;
            if (attempts > maxAttempts) {
                skipped++;
                continue;
            }
            processed++;

            if (!picked.isReady()) {
                MigrationItem failure = item(picked, -1L, "", MigrationStatus.FAILED_RETRYABLE, attempts, "", "Video is not READY in Google Photos");
                ledger.upsert(migrationId, failure);
                failed++;
                continue;
            }

            ledger.upsert(migrationId, item(picked, -1L, previous == null ? "" : previous.sha256(), MigrationStatus.UPLOADING, attempts, "", ""));
            try (PickerApiClient.MediaDownload download = pickerApi.openMedia(sourceToken, picked.baseUrl(), picked.isVideo())) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream digestingStream = new DigestInputStream(download.inputStream(), digest)) {
                    String uploadToken = libraryApi.uploadStream(destinationToken, picked.mimeType(), digestingStream, download.contentLength());
                    String sha256 = HexFormat.of().formatHex(digest.digest());
                    LibraryUploadClient.CreatedMediaItem created = libraryApi.createMediaItem(destinationToken, uploadToken, picked.fileName());
                    ledger.upsert(migrationId, item(picked, download.contentLength(), sha256, MigrationStatus.VERIFIED, attempts, created.id(), ""));
                    verified++;
                }
            } catch (Exception e) {
                MigrationStatus status = attempts >= maxAttempts ? MigrationStatus.FAILED_FINAL : MigrationStatus.FAILED_RETRYABLE;
                ledger.upsert(migrationId, item(picked, previous == null ? -1L : previous.sizeBytes(), previous == null ? "" : previous.sha256(), status, attempts, previous == null ? "" : previous.destinationRef(), safeMessage(e)));
                failed++;
            }
        }
        return new TransferRunResult(migrationId, sessionId, selected.size(), processed, verified, skipped, failed);
    }

    private MigrationItem item(PickedMediaItem picked, long size, String sha, MigrationStatus status, int attempts, String destinationRef, String error) {
        return new MigrationItem(picked.id(), picked.fileName(), picked.mimeType(), size, sha, status, attempts,
                picked.id(), destinationRef, Instant.now(), error);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
