package com.hybrid.tests.scenarios;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.reporting.ReportAssert;
import com.hybrid.framework.reporting.ReportEvidenceContext;
import com.hybrid.tests.model.LoginData;
import com.hybrid.tests.pages.LoggedInPage;
import com.hybrid.tests.pages.PracticeLoginPage;
import io.qameta.allure.Allure;

/**
 * One place for the login flow used by both data sources.
 *
 * Cucumber calls the small methods one step at a time. The Excel test calls
 * execute(...) and runs the exact same methods in sequence. Keeping that
 * shared path is important; otherwise the feature-file and Excel tests can
 * slowly become two different test suites.
 */
public final class LoginScenarioExecutor {
    private final UiDriver driver;
    private PracticeLoginPage loginPage;

    public LoginScenarioExecutor(UiDriver driver) {
        this.driver = driver;
    }

    public void openLoginPage() {
        final String url = FrameworkConfig.baseUrl();
        ReportEvidenceContext.action(
                "User opens the login page: " + url,
                new Runnable() {
                    @Override
                    public void run() {
                        loginPage = new PracticeLoginPage(driver).open(url);
                    }
                });
    }

    public void enterUsername(final String username) {
        ReportEvidenceContext.action(
                "User enters username as '" + username + "'",
                new Runnable() {
                    @Override
                    public void run() {
                        page().enterUsername(username);
                    }
                });
    }

    public void enterPassword(final String password) {
        ReportEvidenceContext.action(
                "User enters password as '" + ReportEvidenceContext.sensitiveValue(password) + "'",
                new Runnable() {
                    @Override
                    public void run() {
                        page().enterPassword(password);
                    }
                });
    }

    public void submit() {
        ReportEvidenceContext.action(
                "User clicks the Submit button",
                new Runnable() {
                    @Override
                    public void run() {
                        page().clickSubmit();
                    }
                });
    }

    public void verifySuccessfulLogin() {
        final LoggedInPage successPage = new LoggedInPage(driver);

        ReportEvidenceContext.action(
                "User waits for the successful login page to load",
                new Runnable() {
                    @Override
                    public void run() {
                        successPage.waitUntilLoaded();
                    }
                });

        String currentUrl = driver.getCurrentUrl();
        ReportAssert.assertTrue(
                "Verify successful login URL",
                currentUrl.contains("/logged-in-successfully/"),
                "URL contains '/logged-in-successfully/'",
                currentUrl);

        String actualMessage = successPage.successMessageText();
        ReportAssert.assertTrue(
                "Verify successful login confirmation message",
                actualMessage.toLowerCase().contains("successfully logged in"),
                "Message contains 'successfully logged in'",
                actualMessage);

        boolean logOutVisible = successPage.isLogOutVisible();
        ReportAssert.assertTrue(
                "Verify Log out link is visible",
                logOutVisible,
                "Log out visible = true",
                "Log out visible = " + logOutVisible);
    }

    public void verifyFailedLogin(String expectedMessage) {
        String actualMessage = page().errorMessage();

        ReportAssert.assertEquals(
                "Verify login validation message",
                actualMessage,
                expectedMessage);
    }

    public void execute(LoginData data) {
        Allure.parameter("Test case ID", data.testCaseId());
        ReportEvidenceContext.info(
                "Test data",
                "Test case=" + data.testCaseId()
                        + ", expected result=" + data.expectedResult());

        openLoginPage();
        enterUsername(data.username());
        enterPassword(data.password());
        submit();

        if ("SUCCESS".equalsIgnoreCase(data.expectedResult())) {
            verifySuccessfulLogin();
            return;
        }

        verifyFailedLogin(data.expectedMessage());
    }

    private PracticeLoginPage page() {
        if (loginPage == null) {
            throw new IllegalStateException(
                    "Login page has not been opened. Call openLoginPage() before using the form.");
        }
        return loginPage;
    }
}
