package com.framework.pages;

import com.framework.utils.CommonActions;
import org.openqa.selenium.By;

public class LoginPage {
    private final CommonActions actions = new CommonActions();

    private final By username = By.id("username");
    private final By password = By.id("password");
    private final By submit = By.id("submit");
    private final By successHeading = By.xpath("//h1[normalize-space()='Logged In Successfully']");

    public void open(String url) {
        actions.open(url);
        actions.waitForVisible(username);
    }

    public void enterUsername(String value) {
        actions.sendText(username, value);
    }

    public void enterPassword(String value) {
        actions.sendText(password, value);
    }

    public void clickSubmit() {
        actions.click(submit);
    }

    public boolean isSuccessPageDisplayed() {
        actions.waitForVisible(successHeading);
        return actions.getCurrentUrl().contains("/logged-in-successfully/")
                && actions.getText(successHeading).contains("Logged In Successfully");
    }
}
