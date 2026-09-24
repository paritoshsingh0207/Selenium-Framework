package com.framework.utils;

import com.framework.base.BaseTest;
import com.framework.config.ConfigReader;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommonActions extends BaseTest {

    private static final Logger LOGGER = LogManager.getLogger(CommonActions.class);

    public void open(String url) {
        LOGGER.info("Opening URL: {}", url);
        getPage().navigate(
                url,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT)
        );
        LOGGER.info("Navigation committed: {}", getPage().url());
    }

    public void waitForVisible(String selector) {
        LOGGER.info("Waiting for element to be visible: {}", selector);
        getPage().locator(selector).waitFor();
        LOGGER.info("Element is visible: {}", selector);
    }

    public void click(String selector) {
        LOGGER.info("Clicking element: {}", selector);
        getPage().locator(selector).click();
    }

    public void clickAndWaitForUrl(String selector, String urlPattern) {
        int navigationTimeoutSeconds = ConfigReader.getInt("navigationTimeoutSeconds");
        LOGGER.info("Clicking element and waiting for URL: {} -> {}", selector, urlPattern);

        getPage().locator(selector).click(
                new Locator.ClickOptions().setNoWaitAfter(true)
        );

        getPage().waitForURL(
                urlPattern,
                new Page.WaitForURLOptions()
                        .setTimeout(navigationTimeoutSeconds * 1000.0)
                        .setWaitUntil(WaitUntilState.COMMIT)
        );

        LOGGER.info("Expected URL reached: {}", getPage().url());
    }

    public void sendText(String selector, String text) {
        LOGGER.info("Entering text into element: {}", selector);
        getPage().locator(selector).fill(text);
    }

    public String getText(String selector) {
        LOGGER.debug("Reading text from element: {}", selector);
        return getPage().locator(selector).innerText();
    }

    public boolean isDisplayed(String selector) {
        LOGGER.debug("Checking visibility of element: {}", selector);
        return getPage().locator(selector).isVisible();
    }

    public void selectDropdownByVisibleText(String selector, String visibleText) {
        LOGGER.info("Selecting dropdown option by visible text '{}' from: {}", visibleText, selector);
        getPage().locator(selector).selectOption(new SelectOption().setLabel(visibleText));
    }

    public void selectDropdownByValue(String selector, String value) {
        LOGGER.info("Selecting dropdown option by value '{}' from: {}", value, selector);
        getPage().locator(selector).selectOption(value);
    }

    public void selectRadioButton(String selector) {
        LOGGER.info("Selecting radio button: {}", selector);
        getPage().locator(selector).check();
    }

    public void selectCheckbox(String selector) {
        LOGGER.info("Selecting checkbox: {}", selector);
        getPage().locator(selector).check();
    }

    public void hover(String selector) {
        LOGGER.info("Hovering over element: {}", selector);
        getPage().locator(selector).hover();
    }

    public byte[] takeScreenshot() {
        LOGGER.debug("Capturing screenshot with short failure-handler timeout");
        try {
            return getPage().screenshot(
                    new Page.ScreenshotOptions().setTimeout(3000.0)
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Failure screenshot could not be captured: {}", exception.getMessage());
            return null;
        }
    }
}
