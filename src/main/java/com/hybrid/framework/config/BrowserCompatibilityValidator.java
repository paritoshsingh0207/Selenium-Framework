package com.hybrid.framework.config;

import com.hybrid.framework.enums.AutomationEngine;
import com.hybrid.framework.enums.SupportedBrowser;

public final class BrowserCompatibilityValidator {
    private BrowserCompatibilityValidator() {
    }

    public static void validate(AutomationEngine engine, SupportedBrowser browser) {
        if (engine == AutomationEngine.SELENIUM
                && (browser == SupportedBrowser.CHROMIUM || browser == SupportedBrowser.WEBKIT)) {
            throw new IllegalArgumentException(browser + " is not supported by the Selenium implementation.");
        }
        if (engine == AutomationEngine.PLAYWRIGHT && browser == SupportedBrowser.SAFARI) {
            throw new IllegalArgumentException("Playwright supports WebKit, not the installed Safari browser.");
        }
    }
}
