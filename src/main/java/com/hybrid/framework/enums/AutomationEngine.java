package com.hybrid.framework.enums;

import java.util.Locale;

public enum AutomationEngine {
    SELENIUM,
    PLAYWRIGHT;

    public static AutomationEngine from(String value) {
        String normalized = value == null || value.isBlank() ? "selenium" : value.trim();
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported engine '" + value + "'. Supported: selenium, playwright", exception);
        }
    }
}
