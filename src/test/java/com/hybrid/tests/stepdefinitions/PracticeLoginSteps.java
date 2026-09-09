package com.hybrid.tests.stepdefinitions;

import com.hybrid.framework.core.DriverContext;
import com.hybrid.tests.scenarios.LoginScenarioExecutor;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Cucumber glue for the sample website.
 *
 * These methods stay intentionally thin. They translate readable Gherkin
 * into calls on LoginScenarioExecutor; browser details do not belong here.
 */
public final class PracticeLoginSteps {
    private LoginScenarioExecutor login;

    @Given("the user opens the Practice Test Automation login page")
    public void openLoginPage() {
        login = new LoginScenarioExecutor(DriverContext.get());
        login.openLoginPage();
    }

    @When("the user enters username {string}")
    public void enterUsername(String username) {
        scenario().enterUsername(username);
    }

    @When("the user enters password {string}")
    public void enterPassword(String password) {
        scenario().enterPassword(password);
    }

    @When("the user clicks the Submit button")
    public void clickSubmit() {
        scenario().submit();
    }

    @Then("the login should be successful")
    public void verifySuccessfulLogin() {
        scenario().verifySuccessfulLogin();
    }

    @Then("the login should fail with message {string}")
    public void verifyFailedLogin(String expectedMessage) {
        scenario().verifyFailedLogin(expectedMessage);
    }

    private LoginScenarioExecutor scenario() {
        if (login == null) {
            throw new IllegalStateException(
                    "The login scenario was used before the Given step opened the page");
        }
        return login;
    }
}
