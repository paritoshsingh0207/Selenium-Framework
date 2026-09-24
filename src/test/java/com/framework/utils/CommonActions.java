package com.framework.utils;

import com.framework.base.BaseTest;
import com.framework.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class CommonActions extends BaseTest {

    private static final Logger LOGGER = LogManager.getLogger(CommonActions.class);

    private WebDriverWait webDriverWait() {
        return new WebDriverWait(
                getDriver(),
                Duration.ofSeconds(ConfigReader.getInt("explicitWaitSeconds"))
        );
    }

    public void open(String url) {
        LOGGER.info("Opening URL: {}", url);
        getDriver().get(url);
    }

    public void waitForVisible(By locator) {
        LOGGER.info("Waiting for element to be visible: {}", locator);
        webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        LOGGER.info("Element is visible: {}", locator);
    }

    public void click(By locator) {
        LOGGER.info("Clicking element: {}", locator);
        webDriverWait().until(ExpectedConditions.elementToBeClickable(locator)).click();
    }

    public void sendText(By locator, String text) {
        LOGGER.info("Entering text into element: {}", locator);
        WebElement element = webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        element.sendKeys(text);
    }

    public String getText(By locator) {
        LOGGER.debug("Reading text from element: {}", locator);
        return webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator)).getText();
    }

    public boolean isDisplayed(By locator) {
        LOGGER.debug("Checking visibility of element: {}", locator);
        return webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator)).isDisplayed();
    }

    public String getCurrentUrl() {
        return getDriver().getCurrentUrl();
    }

    public void selectDropdownByVisibleText(By locator, String visibleText) {
        LOGGER.info("Selecting dropdown option by visible text '{}' from: {}", visibleText, locator);
        WebElement element = webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        new Select(element).selectByVisibleText(visibleText);
    }

    public void selectDropdownByValue(By locator, String value) {
        LOGGER.info("Selecting dropdown option by value '{}' from: {}", value, locator);
        WebElement element = webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        new Select(element).selectByValue(value);
    }

    public void selectRadioButton(By locator) {
        LOGGER.info("Selecting radio button: {}", locator);
        WebElement element = webDriverWait().until(ExpectedConditions.elementToBeClickable(locator));
        if (!element.isSelected()) {
            element.click();
        }
    }

    public void selectCheckbox(By locator) {
        LOGGER.info("Selecting checkbox: {}", locator);
        WebElement element = webDriverWait().until(ExpectedConditions.elementToBeClickable(locator));
        if (!element.isSelected()) {
            element.click();
        }
    }

    public void hover(By locator) {
        LOGGER.info("Hovering over element: {}", locator);
        WebElement element = webDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
        new Actions(getDriver()).moveToElement(element).perform();
    }

    public byte[] takeScreenshot() {
        LOGGER.debug("Capturing screenshot");
        return ((TakesScreenshot) getDriver()).getScreenshotAs(OutputType.BYTES);
    }
}
