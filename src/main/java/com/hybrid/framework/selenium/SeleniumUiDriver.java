package com.hybrid.framework.selenium;

import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import com.hybrid.framework.enums.SupportedBrowser;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class SeleniumUiDriver implements UiDriver {
    private final WebDriver driver;
    private final WebDriverWait wait;

    public SeleniumUiDriver(SupportedBrowser browser, boolean headless, Duration timeout) {
        this.driver = createDriver(browser, headless);
        this.wait = new WebDriverWait(driver, timeout);
        driver.manage().timeouts().pageLoadTimeout(timeout.plusSeconds(20));
        if (!headless && browser != SupportedBrowser.SAFARI) {
            driver.manage().window().maximize();
        }
    }

    private WebDriver createDriver(SupportedBrowser browser, boolean headless) {
        return switch (browser) {
            case CHROME -> SeleniumBrowserOptions.chrome(headless);
            case EDGE -> SeleniumBrowserOptions.edge(headless);
            case FIREFOX -> SeleniumBrowserOptions.firefox(headless);
            case SAFARI -> {
                if (headless) {
                    throw new IllegalArgumentException("Safari does not support this headless configuration");
                }
                yield new SafariDriver();
            }
            default -> throw new IllegalArgumentException("Unsupported Selenium browser: " + browser);
        };
    }

    private By convert(UiLocator locator) {
        return switch (locator.type()) {
            case CSS -> By.cssSelector(locator.value());
            case XPATH -> By.xpath(locator.value());
            case ID -> By.id(locator.value());
            case NAME -> By.name(locator.value());
            case TEST_ID -> By.cssSelector("[data-test=" + cssQuoted(locator.value()) + "],"
                    + "[data-testid=" + cssQuoted(locator.value()) + "]");
            case TEXT -> By.xpath("//*[normalize-space()=" + xpathLiteral(locator.value()) + "]");
            case ROLE -> By.xpath("//*[@role=" + xpathLiteral(locator.value()) + " and ("
                    + "@aria-label=" + xpathLiteral(locator.accessibleName())
                    + " or normalize-space(.)=" + xpathLiteral(locator.accessibleName()) + ")]");
        };
    }

    @Override
    public void navigate(String url) {
        driver.get(url);
    }

    @Override
    public void click(UiLocator locator) {
        wait.until(ExpectedConditions.elementToBeClickable(convert(locator))).click();
    }

    @Override
    public void fill(UiLocator locator, String value) {
        WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(convert(locator)));
        element.clear();
        element.sendKeys(value);
    }

    @Override
    public void clear(UiLocator locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(convert(locator))).clear();
    }

    @Override
    public String getText(UiLocator locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(convert(locator))).getText();
    }

    @Override
    public String getAttribute(UiLocator locator, String attributeName) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(convert(locator)))
                .getAttribute(attributeName);
    }

    @Override
    public boolean isVisible(UiLocator locator) {
        List<WebElement> elements = driver.findElements(convert(locator));
        return elements.stream().anyMatch(WebElement::isDisplayed);
    }

    @Override
    public boolean isEnabled(UiLocator locator) {
        List<WebElement> elements = driver.findElements(convert(locator));
        return elements.stream().filter(WebElement::isDisplayed).anyMatch(WebElement::isEnabled);
    }

    @Override
    public void waitForVisible(UiLocator locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(convert(locator)));
    }

    @Override
    public void selectByValue(UiLocator locator, String value) {
        new Select(wait.until(ExpectedConditions.elementToBeClickable(convert(locator))))
                .selectByValue(value);
    }

    @Override
    public void press(UiLocator locator, String key) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(convert(locator)))
                .sendKeys(Keys.valueOf(key.trim().toUpperCase()));
    }

    @Override
    public String getTitle() {
        return driver.getTitle();
    }

    @Override
    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    @Override
    public byte[] takeScreenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public void saveScreenshot(Path destination) {
        try {
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.write(destination, takeScreenshot());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Selenium screenshot", exception);
        }
    }

    @Override
    public void close() {
        driver.quit();
    }

    private String cssQuoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        String[] parts = value.split("'", -1);
        StringBuilder expression = new StringBuilder("concat(");
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                expression.append(", \"'\", ");
            }
            expression.append("'").append(parts[index]).append("'");
        }
        return expression.append(")").toString();
    }
}
