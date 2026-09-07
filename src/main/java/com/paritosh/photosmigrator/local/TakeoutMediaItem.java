package com.paritosh.photosmigrator.local;

import java.time.Instant;

public record TakeoutMediaItem(
        String id,
        String archivePath,
        String entryName,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        Instant takenTime,
        boolean duplicate,
        String duplicateOf
) {
    public String sourceRef() {
        return archivePath + "!" + entryName;
    }
}
