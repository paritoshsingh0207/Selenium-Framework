package com.hybrid.framework.enums;

import java.util.Locale;

public enum SupportedBrowser {
    CHROME,
    CHROMIUM,
    EDGE,
    FIREFOX,
    WEBKIT,
    SAFARI;

    public static SupportedBrowser from(String value) {
        String normalized = value == null || value.trim().isEmpty() ? "chrome" : value.trim();
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            // A common configuration mistake is to pass "selenium" or "playwright"
            // as the browser. Those values belong to -Dengine, not -Dbrowser.
            if ("selenium".equalsIgnoreCase(normalized) || "playwright".equalsIgnoreCase(normalized)) {
                throw new IllegalArgumentException(
                        "Invalid browser value '" + value + "'. '" + normalized
                                + "' is an automation engine, not a browser. "
                                + "Example: -Dengine=" + normalized + " -Dbrowser="
                                + ("playwright".equalsIgnoreCase(normalized) ? "chromium" : "chrome")
                                + ". Supported browsers: chrome, chromium, edge, firefox, webkit, safari.",
                        exception);
            }

            throw new IllegalArgumentException(
                    "Unsupported browser '" + value
                            + "'. Supported browsers: chrome, chromium, edge, firefox, webkit, safari.",
                    exception);
        }
    }
}
