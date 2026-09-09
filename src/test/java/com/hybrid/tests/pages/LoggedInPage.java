package com.hybrid.tests.pages;

import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import com.hybrid.framework.enums.LocatorType;

/**
 * Small page object for the successful-login screen.
 *
 * We check more than just the URL. A redirect can succeed while the page
 * itself is still broken, so the heading, success text and Log out link are
 * also useful signals.
 */
public final class LoggedInPage {

    private static final UiLocator HEADING = UiLocator.builder("LoggedInPage.heading")
            .primary(LocatorType.XPATH, "//h1[normalize-space()='Logged In Successfully']")
            .fallback(LocatorType.TEXT, "Logged In Successfully")
            .build();

    private static final UiLocator SUCCESS_MESSAGE = UiLocator.builder("LoggedInPage.successMessage")
            .primary(LocatorType.XPATH, "//p[contains(normalize-space(.),'successfully logged in')]")
            .fallback(LocatorType.CSS, ".post-content p")
            .build();

    private static final UiLocator LOG_OUT = UiLocator.builder("LoggedInPage.logOut")
            .primary(LocatorType.XPATH, "//a[normalize-space()='Log out']")
            .fallback(LocatorType.TEXT, "Log out")
            .build();

    private final UiDriver driver;

    public LoggedInPage(UiDriver driver) {
        this.driver = driver;
    }

    public LoggedInPage waitUntilLoaded() {
        // Selenium needs an explicit wait here; Playwright will naturally wait
        // for the same element through its locator API.
        driver.waitForVisible(HEADING);
        return this;
    }

    public boolean hasSuccessUrl() {
        return driver.getCurrentUrl().contains("/logged-in-successfully/");
    }

    public boolean hasSuccessMessage() {
        return driver.getText(SUCCESS_MESSAGE)
                .toLowerCase()
                .contains("successfully logged in");
    }

    public boolean isLogOutVisible() {
        return driver.isVisible(LOG_OUT);
    }
}
