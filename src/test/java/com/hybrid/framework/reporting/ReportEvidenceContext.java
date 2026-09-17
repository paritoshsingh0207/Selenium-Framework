package com.hybrid.framework.reporting;

import com.hybrid.framework.config.FrameworkConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-test evidence collector used by the PDF report.
 *
 * ThreadLocal keeps parallel tests isolated. A login test running on one
 * thread cannot mix its steps or assertions with another test running at
 * the same time.
 */
public final class ReportEvidenceContext {
    private static final ThreadLocal<List<ReportEntry>> ENTRIES =
            new ThreadLocal<List<ReportEntry>>() {
                @Override
                protected List<ReportEntry> initialValue() {
                    return new ArrayList<ReportEntry>();
                }
            };

    private ReportEvidenceContext() {
    }

    public static void clear() {
        ENTRIES.remove();
    }

    public static List<ReportEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<ReportEntry>(ENTRIES.get()));
    }

    /**
     * Records one business action and automatically marks it passed or failed.
     * The original exception is rethrown so reporting never changes test behavior.
     */
    public static void action(String description, Runnable action) {
        long started = System.currentTimeMillis();
        try {
            action.run();
            add("ACTION", description, ReportEntryStatus.PASSED,
                    "", "Completed successfully", "", started,
                    System.currentTimeMillis() - started);
        } catch (RuntimeException exception) {
            add("ACTION", description, ReportEntryStatus.FAILED,
                    "", "Action failed", message(exception), started,
                    System.currentTimeMillis() - started);
            throw exception;
        } catch (Error error) {
            add("ACTION", description, ReportEntryStatus.FAILED,
                    "", "Action failed", message(error), started,
                    System.currentTimeMillis() - started);
            throw error;
        }
    }

    public static void info(String description, String details) {
        long now = System.currentTimeMillis();
        add("INFO", description, ReportEntryStatus.INFO,
                "", "", details, now, 0L);
    }

    public static void assertion(String description, boolean passed,
                                 String expected, String actual, String details) {
        long now = System.currentTimeMillis();
        add("ASSERTION", description,
                passed ? ReportEntryStatus.PASSED : ReportEntryStatus.FAILED,
                safe(expected), safe(actual), safe(details), now, 0L);
    }

    /**
     * Passwords and tokens should normally stay masked in a report.
     * For controlled demo data, -Dreport.showSensitiveData=true can expose
     * the exact value when that evidence is intentionally required.
     */
    public static String sensitiveValue(String value) {
        if (FrameworkConfig.showSensitiveReportData()) {
            return safe(value);
        }
        return "<masked>";
    }

    private static void add(String type, String description, ReportEntryStatus status,
                            String expected, String actual, String details,
                            long timestamp, long durationMs) {
        List<ReportEntry> entries = ENTRIES.get();
        entries.add(new ReportEntry(
                entries.size() + 1,
                safe(type),
                safe(description),
                status,
                safe(expected),
                safe(actual),
                safe(details),
                timestamp,
                Math.max(0L, durationMs)));
    }

    private static String message(Throwable throwable) {
        if (throwable == null) return "";
        String value = throwable.getMessage();
        return value == null ? throwable.getClass().getSimpleName()
                : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
