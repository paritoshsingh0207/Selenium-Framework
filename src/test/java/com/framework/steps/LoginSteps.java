package com.framework.steps;

import com.framework.config.ConfigReader;
import com.framework.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

public class LoginSteps {
    private final LoginPage loginPage = new LoginPage();

    @Given("user opens the login page")
    public void userOpensLoginPage() {
        loginPage.open(ConfigReader.get("baseUrl"));
    }

    @When("user enters username {string}")
    public void userEntersUsername(String username) {
        loginPage.enterUsername(username);
    }

    @When("user enters password {string}")
    public void userEntersPassword(String password) {
        loginPage.enterPassword(password);
    }

    @When("user clicks the submit button")
    public void userClicksSubmitButton() {
        loginPage.clickSubmit();
    }

    @Then("successful login page should be displayed")
    public void successfulLoginPageShouldBeDisplayed() {
        Assert.assertTrue(loginPage.isSuccessPageDisplayed(), "Successful login page was not displayed");
    }
}
