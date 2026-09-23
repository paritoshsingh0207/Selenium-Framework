package com.framework.base;

import com.framework.driver.DriverManager;
import org.openqa.selenium.WebDriver;

public abstract class BaseTest {
    protected WebDriver getDriver() {
        return DriverManager.getDriver();
    }
}
