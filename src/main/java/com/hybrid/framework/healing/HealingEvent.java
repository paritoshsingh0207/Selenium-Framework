package com.hybrid.framework.healing;

import java.time.Instant;

/** Immutable description of one successful locator-healing event. */
public final class HealingEvent {
    private final String locatorName;
    private final String action;
    private final String originalLocator;
    private final String healedLocator;
    private final String engine;
    private final String browser;
    private final String pageUrl;
    private final String threadName;
    private final Instant timestamp;
    private final String screenshotPath;

    public HealingEvent(String locatorName, String action, String originalLocator, String healedLocator,
                        String engine, String browser, String pageUrl, String threadName,
                        Instant timestamp, String screenshotPath) {
        this.locatorName = locatorName;
        this.action = action;
        this.originalLocator = originalLocator;
        this.healedLocator = healedLocator;
        this.engine = engine;
        this.browser = browser;
        this.pageUrl = pageUrl;
        this.threadName = threadName;
        this.timestamp = timestamp;
        this.screenshotPath = screenshotPath;
    }

    public String locatorName() { return locatorName; }
    public String action() { return action; }
    public String originalLocator() { return originalLocator; }
    public String healedLocator() { return healedLocator; }
    public String engine() { return engine; }
    public String browser() { return browser; }
    public String pageUrl() { return pageUrl; }
    public String threadName() { return threadName; }
    public Instant timestamp() { return timestamp; }
    public String screenshotPath() { return screenshotPath; }
}
