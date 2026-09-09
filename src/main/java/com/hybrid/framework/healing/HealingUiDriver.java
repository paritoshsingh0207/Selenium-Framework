package com.hybrid.framework.healing;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.LocatorCandidate;
import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class HealingUiDriver implements UiDriver {
    private static final Logger LOGGER = LogManager.getLogger(HealingUiDriver.class);
    private static final Map<String, LocatorCandidate> CACHE = new ConcurrentHashMap<>();

    private final UiDriver delegate;
    private final String engine;
    private final String browser;

    public HealingUiDriver(UiDriver delegate, String engine, String browser) {
        this.delegate = delegate;
        this.engine = engine;
        this.browser = browser;
    }

    @Override
    public void navigate(String url) {
        delegate.navigate(url);
    }

    @Override
    public void click(UiLocator locator) {
        heal(locator, "click", resolved -> { delegate.click(resolved); return null; });
    }

    @Override
    public void fill(UiLocator locator, String value) {
        heal(locator, "fill", resolved -> { delegate.fill(resolved, value); return null; });
    }

    @Override
    public void clear(UiLocator locator) {
        heal(locator, "clear", resolved -> { delegate.clear(resolved); return null; });
    }

    @Override
    public String getText(UiLocator locator) {
        return heal(locator, "getText", delegate::getText);
    }

    @Override
    public String getAttribute(UiLocator locator, String attributeName) {
        return heal(locator, "getAttribute:" + attributeName,
                resolved -> delegate.getAttribute(resolved, attributeName));
    }

    @Override
    public boolean isVisible(UiLocator locator) {
        if (!shouldHeal(locator)) {
            return delegate.isVisible(locator);
        }
        for (LocatorCandidate candidate : ordered(locator)) {
            try {
                if (delegate.isVisible(locator.resolved(candidate))) {
                    recordIfHealed(locator, candidate, "isVisible");
                    return true;
                }
            } catch (RuntimeException exception) {
                if (!LocatorFailureClassifier.isLocatorFailure(exception)) {
                    throw exception;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isEnabled(UiLocator locator) {
        return heal(locator, "isEnabled", delegate::isEnabled);
    }

    @Override
    public void waitForVisible(UiLocator locator) {
        heal(locator, "waitForVisible", resolved -> { delegate.waitForVisible(resolved); return null; });
    }

    @Override
    public void selectByValue(UiLocator locator, String value) {
        heal(locator, "selectByValue", resolved -> { delegate.selectByValue(resolved, value); return null; });
    }

    @Override
    public void press(UiLocator locator, String key) {
        heal(locator, "press:" + key, resolved -> { delegate.press(resolved, key); return null; });
    }

    @Override
    public String getTitle() {
        return delegate.getTitle();
    }

    @Override
    public String getCurrentUrl() {
        return delegate.getCurrentUrl();
    }

    @Override
    public byte[] takeScreenshot() {
        return delegate.takeScreenshot();
    }

    @Override
    public void saveScreenshot(Path destination) {
        delegate.saveScreenshot(destination);
    }

    @Override
    public void close() {
        delegate.close();
    }

    private <T> T heal(UiLocator locator, String action, Function<UiLocator, T> operation) {
        if (!shouldHeal(locator)) {
            return operation.apply(locator);
        }
        RuntimeException firstFailure = null;
        for (LocatorCandidate candidate : ordered(locator)) {
            try {
                T result = operation.apply(locator.resolved(candidate));
                recordIfHealed(locator, candidate, action);
                return result;
            } catch (RuntimeException exception) {
                if (!LocatorFailureClassifier.isLocatorFailure(exception)) {
                    throw exception;
                }
                if (firstFailure == null) {
                    firstFailure = exception;
                }
                LOGGER.warn("LOCATOR_ATTEMPT_FAILED name={} action={} candidate={} exception={}",
                        locator.name(), action, candidate.description(), exception.getClass().getSimpleName());
            }
        }
        LOGGER.error("SELF_HEALING_FAILED name={} action={}", locator.name(), action);
        if (firstFailure != null) {
            throw firstFailure;
        }
        throw new IllegalStateException("No locator candidates available for " + locator.name());
    }

    private boolean shouldHeal(UiLocator locator) {
        return FrameworkConfig.selfHealingEnabled() && locator.healingEnabled() && !locator.fallbacks().isEmpty();
    }

    private List<LocatorCandidate> ordered(UiLocator locator) {
        LinkedHashSet<LocatorCandidate> ordered = new LinkedHashSet<>();
        if (FrameworkConfig.selfHealingCacheEnabled()) {
            LocatorCandidate cached = CACHE.get(cacheKey(locator));
            if (cached != null) {
                ordered.add(cached);
            }
        }
        ordered.add(locator.primary());
        ordered.addAll(locator.fallbacks());
        return new ArrayList<>(ordered).stream()
                .limit(FrameworkConfig.selfHealingMaximumCandidates())
                .toList();
    }

    private void recordIfHealed(UiLocator locator, LocatorCandidate successful, String action) {
        if (successful.equals(locator.primary())) {
            return;
        }
        if (FrameworkConfig.selfHealingCacheEnabled()) {
            CACHE.put(cacheKey(locator), successful);
        }
        Path screenshot = capture(locator);
        HealingEvent event = new HealingEvent(locator.name(), action, locator.primary().description(),
                successful.description(), engine, browser, safeUrl(), Thread.currentThread().getName(),
                Instant.now(), screenshot == null ? "" : screenshot.toAbsolutePath().toString());
        HealingEventStore.add(event);
        LOGGER.warn("SELF_HEALING_SUCCESS name={} action={} original=\"{}\" healed=\"{}\" screenshot={}",
                event.locatorName(), event.action(), event.originalLocator(), event.healedLocator(),
                event.screenshotPath());
        attach(event, screenshot);
    }

    private Path capture(UiLocator locator) {
        if (!FrameworkConfig.selfHealingScreenshotEnabled()) {
            return null;
        }
        Path destination = FrameworkConfig.artifactsDirectory().resolve("self-healing")
                .resolve(engine.toLowerCase()).resolve(browser.toLowerCase())
                .resolve(locator.name().replaceAll("[^a-zA-Z0-9._-]", "_")
                        + "-" + System.currentTimeMillis() + ".png");
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, delegate.takeScreenshot());
            return destination;
        } catch (Exception exception) {
            LOGGER.error("Unable to capture healing screenshot", exception);
            return null;
        }
    }

    private void attach(HealingEvent event, Path screenshot) {
        String details = "Locator: " + event.locatorName() + "\nAction: " + event.action()
                + "\nOriginal: " + event.originalLocator() + "\nHealed: " + event.healedLocator()
                + "\nEngine: " + event.engine() + "\nBrowser: " + event.browser()
                + "\nURL: " + event.pageUrl() + "\nTimestamp: " + event.timestamp();
        Allure.addAttachment("Self-healing: " + event.locatorName(), "text/plain", details, ".txt");
        if (screenshot != null && Files.exists(screenshot)) {
            try {
                Allure.addAttachment("Self-healing screenshot", "image/png",
                        new ByteArrayInputStream(Files.readAllBytes(screenshot)), ".png");
            } catch (Exception exception) {
                LOGGER.error("Unable to attach healing screenshot", exception);
            }
        }
    }

    private String safeUrl() {
        try {
            return delegate.getCurrentUrl();
        } catch (RuntimeException exception) {
            return "<not available>";
        }
    }

    private String cacheKey(UiLocator locator) {
        return engine + "|" + browser + "|" + locator.name();
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
