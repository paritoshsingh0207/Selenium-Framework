package com.hybrid.framework.healing;

import java.time.Instant;

public record HealingEvent(
        String locatorName,
        String action,
        String originalLocator,
        String healedLocator,
        String engine,
        String browser,
        String pageUrl,
        String threadName,
        Instant timestamp,
        String screenshotPath) {
}
