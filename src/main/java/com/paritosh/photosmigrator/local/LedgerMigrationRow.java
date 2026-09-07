package com.paritosh.photosmigrator.local;

import java.time.Instant;

public record LedgerMigrationRow(
        String id,
        String archivePath,
        String entryName,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        Instant takenTime,
        boolean duplicate,
        String status,
        String destinationAccount,
        String destinationRef,
        String error,
        int attempts
) {
    public TakeoutMediaItem toTakeoutMediaItem() {
        return new TakeoutMediaItem(id, archivePath, entryName, fileName, mimeType, sizeBytes, sha256,
                takenTime, duplicate, "");
    }
}
