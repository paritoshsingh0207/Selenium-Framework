package com.paritosh.photosmigrator.local;

public record CleanupBatch(
        String batch,
        String from,
        String to,
        String destinations,
        long expected,
        long verified,
        String status
) { }
