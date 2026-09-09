package com.hybrid.framework.logging;

import com.hybrid.framework.core.UiLocator;

public final class LogSanitizer {
    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
    }

    public static String value(UiLocator locator, String value) {
        if (value == null) {
            return "<null>";
        }
        if (locator != null && locator.isSensitive()) {
            return "<masked>";
        }
        return text(value);
    }

    public static String text(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH) + "...<truncated>";
    }
}
