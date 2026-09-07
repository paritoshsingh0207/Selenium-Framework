package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMigrationRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadsCreatesCheckpointsAndVerifiesPlannedPhoto() {
        Path ledger = plannedLedger("photo", "image/jpeg", "dest@example.com");
        AtomicInteger uploads = new AtomicInteger();
        MediaTransferClient transfer = new MediaTransferClient() {
            @Override public String uploadBytes(AccessTokenProvider tokens, TakeoutMediaItem item) {
                uploads.incrementAndGet(); return "upload-token";
            }
            @Override public CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String token, String fileName) {
                return new CreatedMediaItem("media-1", "", "");
            }
            @Override public DestinationMediaItem verify(AccessTokenProvider tokens, String id) {
                return new DestinationMediaItem(id, "image/jpeg", "");
            }
        };

        LocalMigrationRunResult result = new LocalMigrationRunner(authenticator(), transfer).run(ledger, 5);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.uploaded()).isEqualTo(1);
        assertThat(result.verified()).isEqualTo(1);
        assertThat(uploads).hasValue(1);
        try (LocalMigrationLedger saved = LocalMigrationLedger.open(ledger, 1)) {
            LedgerMigrationRow row = saved.rows().getFirst();
            assertThat(row.status()).isEqualTo("VERIFIED");
            assertThat(row.destinationRef()).isEqualTo("media-1");
            assertThat(row.attempts()).isEqualTo(1);
        }
    }

    @Test
    void neverReuploadsAnUncertainCreatingMediaState() {
        Path ledger = plannedLedger("photo", "image/jpeg", "dest@example.com");
        try (LocalMigrationLedger state = LocalMigrationLedger.open(ledger, 1)) {
            state.update("photo", "CREATING_MEDIA", "upload-token:uncertain", "", true, true);
        }
        AtomicInteger calls = new AtomicInteger();
        MediaTransferClient transfer = new MediaTransferClient() {
            @Override public String uploadBytes(AccessTokenProvider tokens, TakeoutMediaItem item) { calls.incrementAndGet(); return "x"; }
            @Override public CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String token, String fileName) { calls.incrementAndGet(); return null; }
            @Override public DestinationMediaItem verify(AccessTokenProvider tokens, String id) { calls.incrementAndGet(); return null; }
        };

        LocalMigrationRunResult result = new LocalMigrationRunner(authenticator(), transfer).run(ledger, 5);

        assertThat(result.processed()).isZero();
        assertThat(result.reconciliationRequired()).isEqualTo(1);
        assertThat(calls).hasValue(0);
        try (LocalMigrationLedger saved = LocalMigrationLedger.open(ledger, 1)) {
            assertThat(saved.rows().getFirst().status()).isEqualTo("NEEDS_RECONCILIATION");
        }
    }

    @Test
    void waitingVideoIsVerifiedOnLaterRunWithoutReupload() {
        Path ledger = plannedLedger("video", "video/mp4", "dest@example.com");
        AtomicInteger uploads = new AtomicInteger();
        AtomicInteger verifies = new AtomicInteger();
        MediaTransferClient processing = new MediaTransferClient() {
            @Override public String uploadBytes(AccessTokenProvider tokens, TakeoutMediaItem item) { uploads.incrementAndGet(); return "u"; }
            @Override public CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String token, String fileName) {
                return new CreatedMediaItem("video-1", "", "PROCESSING");
            }
            @Override public DestinationMediaItem verify(AccessTokenProvider tokens, String id) {
                verifies.incrementAndGet(); return new DestinationMediaItem(id, "video/mp4", "PROCESSING");
            }
        };
        LocalMigrationRunResult first = new LocalMigrationRunner(authenticator(), processing).run(ledger, 5);
        assertThat(first.waiting()).isEqualTo(1);
        assertThat(uploads).hasValue(1);

        MediaTransferClient ready = new MediaTransferClient() {
            @Override public String uploadBytes(AccessTokenProvider tokens, TakeoutMediaItem item) { uploads.incrementAndGet(); return "unexpected"; }
            @Override public CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String token, String fileName) { throw new AssertionError(); }
            @Override public DestinationMediaItem verify(AccessTokenProvider tokens, String id) {
                verifies.incrementAndGet(); return new DestinationMediaItem(id, "video/mp4", "READY");
            }
        };
        LocalMigrationRunResult second = new LocalMigrationRunner(authenticator(), ready).run(ledger, 5);

        assertThat(second.verified()).isEqualTo(1);
        assertThat(uploads).hasValue(1);
        assertThat(verifies).hasValue(1);
    }

    private Path plannedLedger(String id, String mime, String destination) {
        Path ledger = tempDir.resolve(id + ".xlsx");
        TakeoutMediaItem item = new TakeoutMediaItem(id, tempDir.resolve("takeout.zip").toString(),
                "Photos/" + id, id, mime, 100, "hash-" + id, null, false, "");
        TakeoutInventoryLedger service = new TakeoutInventoryLedger();
        service.write(ledger, List.of(item));
        AllocationResult allocation = new AllocationResult(
                List.of(new AllocationResult.Assignment(id, destination, 100)), List.of(),
                java.util.Map.of(destination, 100L), java.util.Map.of(destination, 1000L));
        service.applyAllocation(ledger, allocation);
        return ledger;
    }

    private DestinationAuthenticator authenticator() {
        return expected -> new DestinationAuthenticator.AuthenticatedDestination(expected, new AccessTokenProvider() {
            @Override public String accessToken() { return "token"; }
            @Override public String refreshAccessToken() { return "refresh"; }
        });
    }
}
