package com.paritosh.photosmigrator.model;

public enum MigrationStatus {
    DISCOVERED,
    DOWNLOADING,
    DOWNLOADED,
    HASHED,
    UPLOADING,
    UPLOADED,
    VERIFIED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    SKIPPED_DUPLICATE
}
