package com.paritosh.photosmigrator.model;

public record TransferRunResult(
        String migrationId,
        String sessionId,
        int selected,
        int processed,
        int verified,
        int skipped,
        int failed
) { }
