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
            throw new IllegalArgumentException("Unsupported browser '" + value + "'.", exception);
        }
    }
}
