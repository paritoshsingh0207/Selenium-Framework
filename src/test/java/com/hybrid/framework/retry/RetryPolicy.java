package com.hybrid.framework.retry;

import com.hybrid.framework.config.FrameworkConfig;
import com.microsoft.playwright.PlaywrightException;
import org.openqa.selenium.WebDriverException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class RetryPolicy {
    private RetryPolicy() {
    }

    public static boolean shouldRetry(Throwable throwable) {
        if (throwable == null) return false;
        if (FrameworkConfig.retryAllFailures()) return true;
        if (contains(throwable, AssertionError.class)) return FrameworkConfig.retryAssertionFailures();
        return contains(throwable, WebDriverException.class)
                || contains(throwable, PlaywrightException.class)
                || contains(throwable, IOException.class);
    }

    private static boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
