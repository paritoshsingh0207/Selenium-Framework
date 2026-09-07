package com.paritosh.photosmigrator.model;

import java.time.Instant;

public record MigrationItem(
        String id,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        MigrationStatus status,
        int attempts,
        String sourceRef,
        String destinationRef,
        Instant lastUpdated,
        String error
) {
    public MigrationItem withStatus(MigrationStatus nextStatus, String nextError) {
        return new MigrationItem(id, fileName, mimeType, sizeBytes, sha256, nextStatus, attempts,
                sourceRef, destinationRef, Instant.now(), nextError);
    }
}
