package com.framework.pages;

import com.framework.utils.CommonActions;

public class LoginPage {
    private final CommonActions actions = new CommonActions();

    private final String username = "#username";
    private final String password = "#password";
    private final String submit = "#submit";
    private final String successHeading = ".post-title";

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
        actions.clickAndWaitForUrl(submit, "**/logged-in-successfully/**");
    }

    public boolean isSuccessPageDisplayed() {
        actions.waitForVisible(successHeading);
        return actions.getText(successHeading).contains("Logged In Successfully");
    }
}
