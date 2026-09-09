package com.hybrid.framework.config;

import com.hybrid.framework.enums.AutomationEngine;
import com.hybrid.framework.enums.SupportedBrowser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FrameworkConfig {
    private static final Set<String> DATA_SOURCES = immutableSet("feature", "excel", "both");
    private static final Set<String> PARALLEL_MODES = immutableSet("none", "methods", "classes", "tests", "instances");

    private FrameworkConfig() {
    }

    public static AutomationEngine engine() {
        return AutomationEngine.from(System.getProperty("engine", "selenium"));
    }

    public static SupportedBrowser browser() {
        return SupportedBrowser.from(System.getProperty("browser", "chrome"));
    }

    public static String dataSource() {
        String value = System.getProperty("data.source", "feature").trim().toLowerCase(Locale.ROOT);
        if (!DATA_SOURCES.contains(value)) {
            throw new IllegalArgumentException("data.source must be feature, excel, or both");
        }
        return value;
    }

    public static boolean headless() {
        return Boolean.parseBoolean(System.getProperty("headless", "true"));
    }

    public static Duration timeout() {
        return Duration.ofSeconds(positiveLong("timeout", 10));
    }

    public static String baseUrl() {
        return System.getProperty("baseUrl", "https://practicetestautomation.com/practice-test-login/");
    }

    public static int retryCount() {
        return nonNegativeInt("retry.count", 1);
    }

    public static boolean retryAssertionFailures() {
        return Boolean.parseBoolean(System.getProperty("retry.assertions", "false"));
    }

    public static boolean retryAllFailures() {
        return Boolean.parseBoolean(System.getProperty("retry.allFailures", "false"));
    }

    public static boolean screenshotOnFailure() {
        return Boolean.parseBoolean(System.getProperty("screenshot.onFailure", "true"));
    }

    public static String parallelMode() {
        String value = System.getProperty("parallel.mode", "methods").trim().toLowerCase(Locale.ROOT);
        if (!PARALLEL_MODES.contains(value)) {
            throw new IllegalArgumentException("Unsupported parallel.mode: " + value);
        }
        return value;
    }

    public static int threadCount() {
        return positiveInt("thread.count", 4);
    }

    public static int dataProviderThreadCount() {
        return positiveInt("dataprovider.thread.count", threadCount());
    }

    public static boolean selfHealingEnabled() {
        return Boolean.parseBoolean(System.getProperty("self.healing.enabled", "true"));
    }

    public static boolean selfHealingCacheEnabled() {
        return Boolean.parseBoolean(System.getProperty("self.healing.cache", "true"));
    }

    public static boolean selfHealingScreenshotEnabled() {
        return Boolean.parseBoolean(System.getProperty("self.healing.screenshot", "true"));
    }

    public static int selfHealingMaximumCandidates() {
        return positiveInt("self.healing.maxCandidates", 5);
    }

    public static boolean pdfReportEnabled() {
        return Boolean.parseBoolean(System.getProperty("pdf.report.enabled", "true"));
    }

    public static Path pdfReportPath() {
        return Paths.get(System.getProperty("pdf.report.path", "target/reports/hybrid-automation-report.pdf"));
    }

    public static Path artifactsDirectory() {
        return Paths.get(System.getProperty("artifacts.dir", "artifacts"));
    }

    public static Path allureResultsDirectory() {
        return Paths.get(System.getProperty("allure.results.directory", "target/allure-results"));
    }

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    private static int positiveInt(String name, int defaultValue) {
        int value = Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private static int nonNegativeInt(String name, int defaultValue) {
        int value = Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    private static long positiveLong(String name, long defaultValue) {
        long value = Long.parseLong(System.getProperty(name, String.valueOf(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }
}
