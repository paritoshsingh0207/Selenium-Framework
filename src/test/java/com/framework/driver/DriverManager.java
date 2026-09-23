package com.framework.driver;

import com.framework.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public final class DriverManager {
    private static final Logger LOGGER = LogManager.getLogger(DriverManager.class);
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<WebDriver>();

    private DriverManager() { }

    public static void startDriver() {
        String browserName = ConfigReader.get("browser").toLowerCase();
        boolean headless = Boolean.parseBoolean(ConfigReader.get("headless"));
        WebDriver driver;

        LOGGER.info("Starting Selenium browser: {} | headless={}", browserName, headless);

        try {
            if ("firefox".equals(browserName)) {
                FirefoxOptions options = new FirefoxOptions();
                if (headless) options.addArguments("-headless");
                driver = new FirefoxDriver(options);
            } else if ("edge".equals(browserName)) {
                EdgeOptions options = new EdgeOptions();
                if (headless) options.addArguments("--headless=new");
                driver = new EdgeDriver(options);
            } else {
                ChromeOptions options = new ChromeOptions();
                if (headless) options.addArguments("--headless=new");
                driver = new ChromeDriver(options);
            }

            if (!headless) driver.manage().window().maximize();
            DRIVER.set(driver);
            LOGGER.info("Selenium browser started successfully");
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to start Selenium browser: {}", browserName, exception);
            throw exception;
        }
    }

    public static boolean hasDriver() { return DRIVER.get() != null; }

    public static WebDriver getDriver() {
        WebDriver driver = DRIVER.get();
        if (driver == null) throw new IllegalStateException("WebDriver has not been started. Check Hooks.");
        return driver;
    }

    public static void quitDriver() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                LOGGER.info("Closing Selenium browser");
                driver.quit();
            } finally {
                DRIVER.remove();
                LOGGER.info("Selenium browser closed");
            }
        }
    }
}
