package com.paritosh.photosmigrator.model;

public enum MigrationStatus {
    DISCOVERED,
    WAITING_SOURCE_READY,
    DOWNLOADING,
    DOWNLOADED,
    HASHED,
    UPLOADING,
    UPLOADED,
    WAITING_DESTINATION_READY,
    VERIFIED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    SKIPPED_DUPLICATE
}
