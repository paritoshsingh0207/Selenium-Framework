package com.hybrid.framework.core;

import java.nio.file.Path;

public interface UiDriver extends AutoCloseable {
    void navigate(String url);
    void click(UiLocator locator);
    void fill(UiLocator locator, String value);
    void clear(UiLocator locator);
    String getText(UiLocator locator);
    String getAttribute(UiLocator locator, String attributeName);
    boolean isVisible(UiLocator locator);
    boolean isEnabled(UiLocator locator);
    void waitForVisible(UiLocator locator);
    void selectByValue(UiLocator locator, String value);
    void press(UiLocator locator, String key);
    String getTitle();
    String getCurrentUrl();
    byte[] takeScreenshot();
    void saveScreenshot(Path destination);
    @Override
    void close();
}
