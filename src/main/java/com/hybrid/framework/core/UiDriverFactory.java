package com.hybrid.framework.core;

import com.hybrid.framework.config.BrowserCompatibilityValidator;
import com.hybrid.framework.enums.AutomationEngine;
import com.hybrid.framework.enums.SupportedBrowser;
import com.hybrid.framework.healing.HealingUiDriver;
import com.hybrid.framework.logging.LoggingUiDriver;
import com.hybrid.framework.playwright.PlaywrightUiDriver;
import com.hybrid.framework.selenium.SeleniumUiDriver;

import java.time.Duration;

public final class UiDriverFactory {
    private UiDriverFactory() {
    }

    public static UiDriver create(AutomationEngine engine, SupportedBrowser browser,
                                  boolean headless, Duration timeout) {
        BrowserCompatibilityValidator.validate(engine, browser);

        UiDriver raw;
        switch (engine) {
            case SELENIUM:
                raw = new SeleniumUiDriver(browser, headless, timeout);
                break;
            case PLAYWRIGHT:
                raw = new PlaywrightUiDriver(browser, headless, timeout);
                break;
            default:
                throw new IllegalArgumentException("Unsupported automation engine: " + engine);
        }

        // Decorators are layered once here so test/page code stays unaware of
        // logging and healing implementation details.
        UiDriver healing = new HealingUiDriver(raw, engine.name(), browser.name());
        return new LoggingUiDriver(healing, engine.name(), browser.name());
    }
}
