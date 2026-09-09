package com.hybrid.tests.pages;

import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import com.hybrid.framework.enums.LocatorType;

/**
 * Page object for the Practice Test Automation login screen.
 *
 * The page object deliberately knows nothing about Selenium or Playwright.
 * That detail stays inside UiDriver, which is what lets the same test run
 * through either engine.
 */
public final class PracticeLoginPage {

    // ID is the first choice because it is short and stable on this page.
    // The extra locators are only there for controlled self-healing.
    private static final UiLocator USERNAME = UiLocator.builder("PracticeLoginPage.username")
            .primary(LocatorType.ID, "username")
            .fallback(LocatorType.NAME, "username")
            .fallback(LocatorType.CSS, "input#username")
            .build();

    private static final UiLocator PASSWORD = UiLocator.builder("PracticeLoginPage.password")
            .primary(LocatorType.ID, "password")
            .fallback(LocatorType.NAME, "password")
            .fallback(LocatorType.CSS, "input#password")
            .build();

    private static final UiLocator SUBMIT = UiLocator.builder("PracticeLoginPage.submit")
            .primary(LocatorType.ID, "submit")
            .fallback(LocatorType.CSS, "button#submit")
            .fallback(LocatorType.XPATH, "//button[normalize-space()='Submit']")
            .fallbackRole("button", "Submit")
            .build();

    private static final UiLocator ERROR_MESSAGE = UiLocator.builder("PracticeLoginPage.errorMessage")
            .primary(LocatorType.ID, "error")
            .fallback(LocatorType.CSS, "#error")
            .fallback(LocatorType.XPATH, "//*[@id='error']")
            .build();

    private final UiDriver driver;

    public PracticeLoginPage(UiDriver driver) {
        this.driver = driver;
    }

    public PracticeLoginPage open(String loginUrl) {
        driver.navigate(loginUrl);
        driver.waitForVisible(USERNAME);
        return this;
    }

    public PracticeLoginPage enterUsername(String username) {
        driver.fill(USERNAME, username);
        return this;
    }

    public PracticeLoginPage enterPassword(String password) {
        driver.fill(PASSWORD, password);
        return this;
    }

    public void clickSubmit() {
        driver.click(SUBMIT);
    }

    public String errorMessage() {
        return driver.getText(ERROR_MESSAGE).trim();
    }
}
