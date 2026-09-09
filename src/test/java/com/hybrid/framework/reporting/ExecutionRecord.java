package com.hybrid.framework.reporting;

public record ExecutionRecord(
        String executionKey,
        String testName,
        String dataIdentity,
        ExecutionStatus status,
        int attempt,
        String engine,
        String browser,
        String threadName,
        long startTime,
        long endTime,
        long durationMs,
        String failureMessage,
        String screenshotPath) {
}
