package com.framework.utils;

import com.framework.base.BaseTest;
import com.microsoft.playwright.options.SelectOption;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommonActions extends BaseTest {

    private static final Logger LOGGER = LogManager.getLogger(CommonActions.class);

    public void open(String url) {
        LOGGER.info("Opening URL: {}", url);
        getPage().navigate(url);
    }

    public void click(String selector) {
        LOGGER.info("Clicking element: {}", selector);
        getPage().locator(selector).click();
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
        LOGGER.debug("Capturing screenshot");
        return getPage().screenshot();
    }
}
