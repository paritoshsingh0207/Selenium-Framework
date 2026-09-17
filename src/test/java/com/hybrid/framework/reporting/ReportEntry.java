package com.hybrid.framework.reporting;

/**
 * One human-readable item written to the detailed PDF report.
 *
 * Actions and assertions use the same model so the report can preserve
 * the exact execution order without coupling the reporter to one website.
 */
public final class ReportEntry {
    private final int sequence;
    private final String type;
    private final String description;
    private final ReportEntryStatus status;
    private final String expected;
    private final String actual;
    private final String details;
    private final long timestamp;
    private final long durationMs;

    public ReportEntry(int sequence, String type, String description,
                       ReportEntryStatus status, String expected, String actual,
                       String details, long timestamp, long durationMs) {
        this.sequence = sequence;
        this.type = type;
        this.description = description;
        this.status = status;
        this.expected = expected;
        this.actual = actual;
        this.details = details;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
    }

    public int sequence() { return sequence; }
    public String type() { return type; }
    public String description() { return description; }
    public ReportEntryStatus status() { return status; }
    public String expected() { return expected; }
    public String actual() { return actual; }
    public String details() { return details; }
    public long timestamp() { return timestamp; }
    public long durationMs() { return durationMs; }
}
