package com.framework.base;

import com.framework.driver.BrowserManager;
import com.microsoft.playwright.Page;

public abstract class BaseTest {
    protected Page getPage() {
        return BrowserManager.getPage();
    }
}
