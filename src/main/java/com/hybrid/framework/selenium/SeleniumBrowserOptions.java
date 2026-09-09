package com.hybrid.framework.selenium;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

public final class SeleniumBrowserOptions {
    private SeleniumBrowserOptions() {
    }

    public static WebDriver chrome(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        commonChromiumArguments(headless).forEach(options::addArguments);
        return new ChromeDriver(options);
    }

    public static WebDriver edge(boolean headless) {
        EdgeOptions options = new EdgeOptions();
        commonChromiumArguments(headless).forEach(options::addArguments);
        return new EdgeDriver(options);
    }

    public static WebDriver firefox(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("-headless");
        }
        options.addArguments("--width=1920", "--height=1080");
        return new FirefoxDriver(options);
    }

    private static java.util.List<String> commonChromiumArguments(boolean headless) {
        java.util.List<String> arguments = new java.util.ArrayList<>();
        if (headless) {
            arguments.add("--headless=new");
        }
        arguments.add("--window-size=1920,1080");
        arguments.add("--disable-dev-shm-usage");
        arguments.add("--no-sandbox");
        return arguments;
    }
}
