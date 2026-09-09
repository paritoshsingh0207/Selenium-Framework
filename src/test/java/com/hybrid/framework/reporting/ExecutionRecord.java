package com.hybrid.framework.reporting;

/** Immutable result of one TestNG test attempt. */
public final class ExecutionRecord {
    private final String executionKey;
    private final String testName;
    private final String dataIdentity;
    private final ExecutionStatus status;
    private final int attempt;
    private final String engine;
    private final String browser;
    private final String threadName;
    private final long startTime;
    private final long endTime;
    private final long durationMs;
    private final String failureMessage;
    private final String screenshotPath;

    public ExecutionRecord(String executionKey, String testName, String dataIdentity,
                           ExecutionStatus status, int attempt, String engine, String browser,
                           String threadName, long startTime, long endTime, long durationMs,
                           String failureMessage, String screenshotPath) {
        this.executionKey = executionKey;
        this.testName = testName;
        this.dataIdentity = dataIdentity;
        this.status = status;
        this.attempt = attempt;
        this.engine = engine;
        this.browser = browser;
        this.threadName = threadName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
        this.failureMessage = failureMessage;
        this.screenshotPath = screenshotPath;
    }

    public String executionKey() { return executionKey; }
    public String testName() { return testName; }
    public String dataIdentity() { return dataIdentity; }
    public ExecutionStatus status() { return status; }
    public int attempt() { return attempt; }
    public String engine() { return engine; }
    public String browser() { return browser; }
    public String threadName() { return threadName; }
    public long startTime() { return startTime; }
    public long endTime() { return endTime; }
    public long durationMs() { return durationMs; }
    public String failureMessage() { return failureMessage; }
    public String screenshotPath() { return screenshotPath; }
}
