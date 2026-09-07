package com.paritosh.photosmigrator.model;

public record PickedMediaItem(
        String id,
        String fileName,
        String mimeType,
        String baseUrl,
        String type,
        String processingStatus
) {
    public boolean isVideo() {
        return "VIDEO".equalsIgnoreCase(type) || (mimeType != null && mimeType.startsWith("video/"));
    }

    public boolean isReady() {
        return !isVideo() || processingStatus == null || processingStatus.isBlank() || "READY".equalsIgnoreCase(processingStatus);
    }
}
