package com.hybrid.framework.healing;

import com.microsoft.playwright.PlaywrightException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

import java.util.HashSet;
import java.util.Set;

public final class LocatorFailureClassifier {
    private LocatorFailureClassifier() {
    }

    public static boolean isLocatorFailure(Throwable throwable) {
        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (current instanceof NoSuchElementException
                    || current instanceof StaleElementReferenceException
                    || current instanceof TimeoutException
                    || current instanceof PlaywrightException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
