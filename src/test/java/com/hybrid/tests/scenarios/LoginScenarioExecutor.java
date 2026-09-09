package com.hybrid.tests.scenarios;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.UiDriver;
import com.hybrid.tests.model.LoginData;
import com.hybrid.tests.pages.LoggedInPage;
import com.hybrid.tests.pages.PracticeLoginPage;
import io.qameta.allure.Allure;
import org.testng.Assert;

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
        loginPage = new PracticeLoginPage(driver)
                .open(FrameworkConfig.baseUrl());
    }

    public void enterUsername(String username) {
        page().enterUsername(username);
    }

    public void enterPassword(String password) {
        page().enterPassword(password);
    }

    public void submit() {
        page().clickSubmit();
    }

    public void verifySuccessfulLogin() {
        LoggedInPage successPage = new LoggedInPage(driver)
                .waitUntilLoaded();

        Assert.assertTrue(
                successPage.hasSuccessUrl(),
                "Expected the browser to land on the successful-login URL, but it was: "
                        + driver.getCurrentUrl());

        Assert.assertTrue(
                successPage.hasSuccessMessage(),
                "The success message was not displayed after login");

        Assert.assertTrue(
                successPage.isLogOutVisible(),
                "The Log out link was not visible after login");
    }

    public void verifyFailedLogin(String expectedMessage) {
        String actualMessage = page().errorMessage();

        Assert.assertEquals(
                actualMessage,
                expectedMessage,
                "Unexpected validation message on the login page");
    }

    public void execute(LoginData data) {
        Allure.parameter("Test case ID", data.testCaseId());

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
