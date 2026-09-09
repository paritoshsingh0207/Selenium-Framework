package com.hybrid.framework.logging;

import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class LoggingUiDriver implements UiDriver {
    private static final Logger LOGGER = LogManager.getLogger(LoggingUiDriver.class);

    private final UiDriver delegate;
    private final String engine;
    private final String browser;

    public LoggingUiDriver(UiDriver delegate, String engine, String browser) {
        this.delegate = delegate;
        this.engine = engine;
        this.browser = browser;
    }

    @Override
    public void navigate(String url) {
        execute("Navigate", "url=" + url, () -> delegate.navigate(url));
    }

    @Override
    public void click(UiLocator locator) {
        execute("Click", "locator=" + locator, () -> delegate.click(locator));
    }

    @Override
    public void fill(UiLocator locator, String value) {
        execute("Fill", "locator=" + locator + ", value=" + LogSanitizer.value(locator, value),
                () -> delegate.fill(locator, value));
    }

    @Override
    public void clear(UiLocator locator) {
        execute("Clear", "locator=" + locator, () -> delegate.clear(locator));
    }

    @Override
    public String getText(UiLocator locator) {
        return query("Get text", "locator=" + locator, () -> delegate.getText(locator));
    }

    @Override
    public String getAttribute(UiLocator locator, String attributeName) {
        return query("Get attribute", "locator=" + locator + ", attribute=" + attributeName,
                () -> delegate.getAttribute(locator, attributeName));
    }

    @Override
    public boolean isVisible(UiLocator locator) {
        return query("Check visibility", "locator=" + locator, () -> delegate.isVisible(locator));
    }

    @Override
    public boolean isEnabled(UiLocator locator) {
        return query("Check enabled", "locator=" + locator, () -> delegate.isEnabled(locator));
    }

    @Override
    public void waitForVisible(UiLocator locator) {
        execute("Wait for visible", "locator=" + locator, () -> delegate.waitForVisible(locator));
    }

    @Override
    public void selectByValue(UiLocator locator, String value) {
        execute("Select option", "locator=" + locator + ", value=" + value,
                () -> delegate.selectByValue(locator, value));
    }

    @Override
    public void press(UiLocator locator, String key) {
        execute("Press key", "locator=" + locator + ", key=" + key, () -> delegate.press(locator, key));
    }

    @Override
    public String getTitle() {
        return query("Get title", "", delegate::getTitle);
    }

    @Override
    public String getCurrentUrl() {
        return query("Get current URL", "", delegate::getCurrentUrl);
    }

    @Override
    public byte[] takeScreenshot() {
        return delegate.takeScreenshot();
    }

    @Override
    public void saveScreenshot(Path destination) {
        execute("Save screenshot", "destination=" + destination, () -> delegate.saveScreenshot(destination));
    }

    @Override
    public void close() {
        execute("Close browser session", "", delegate::close);
    }

    private void execute(String action, String details, Runnable operation) {
        long start = System.nanoTime();
        String step = details.isBlank() ? action : action + " | " + details;
        LOGGER.info("ACTION_START action=\"{}\" engine={} browser={} {}", action, engine, browser, details);
        try {
            Allure.step(step, ignored -> operation.run());
            LOGGER.info("ACTION_SUCCESS action=\"{}\" durationMs={}", action, elapsed(start));
        } catch (RuntimeException exception) {
            LOGGER.error("ACTION_FAILURE action=\"{}\" durationMs={} details=\"{}\"",
                    action, elapsed(start), details, exception);
            throw exception;
        }
    }

    private <T> T query(String action, String details, Supplier<T> operation) {
        long start = System.nanoTime();
        AtomicReference<T> value = new AtomicReference<>();
        String step = details.isBlank() ? action : action + " | " + details;
        LOGGER.info("QUERY_START action=\"{}\" engine={} browser={} {}", action, engine, browser, details);
        try {
            Allure.step(step, ignored -> value.set(operation.get()));
            T result = value.get();
            String safe = result instanceof byte[] bytes ? "byte[" + bytes.length + "]"
                    : LogSanitizer.text(String.valueOf(result));
            LOGGER.info("QUERY_SUCCESS action=\"{}\" durationMs={} result=\"{}\"", action, elapsed(start), safe);
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error("QUERY_FAILURE action=\"{}\" durationMs={} details=\"{}\"",
                    action, elapsed(start), details, exception);
            throw exception;
        }
    }

    private long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
