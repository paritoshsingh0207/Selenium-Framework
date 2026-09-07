package com.paritosh.photosmigrator.local;

public record LocalMigrationRunResult(
        int processed,
        int uploaded,
        int verified,
        int waiting,
        int failed,
        int reconciliationRequired
) { }
