package com.hybrid.framework.playwright;

import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiLocator;
import com.hybrid.framework.enums.SupportedBrowser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.nio.file.Path;
import java.time.Duration;

public final class PlaywrightUiDriver implements UiDriver {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    public PlaywrightUiDriver(SupportedBrowser browserType, boolean headless, Duration timeout) {
        playwright = Playwright.create();
        com.microsoft.playwright.BrowserType.LaunchOptions options =
                new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(headless);
        browser = launch(browserType, options);
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        page = context.newPage();
        page.setDefaultTimeout(timeout.toMillis());
        page.setDefaultNavigationTimeout(timeout.plusSeconds(20).toMillis());
    }

    private Browser launch(SupportedBrowser type, com.microsoft.playwright.BrowserType.LaunchOptions options) {
        return switch (type) {
            case CHROMIUM -> playwright.chromium().launch(options);
            case CHROME -> playwright.chromium().launch(options.setChannel("chrome"));
            case EDGE -> playwright.chromium().launch(options.setChannel("msedge"));
            case FIREFOX -> playwright.firefox().launch(options);
            case WEBKIT -> playwright.webkit().launch(options);
            default -> throw new IllegalArgumentException("Unsupported Playwright browser: " + type);
        };
    }

    private Locator convert(UiLocator locator) {
        return switch (locator.type()) {
            case CSS -> page.locator(locator.value());
            case XPATH -> page.locator("xpath=" + locator.value());
            case ID -> page.locator("#" + cssEscape(locator.value()));
            case NAME -> page.locator("[name=" + cssQuoted(locator.value()) + "]");
            case TEST_ID -> page.locator("[data-test=" + cssQuoted(locator.value()) + "],"
                    + "[data-testid=" + cssQuoted(locator.value()) + "]");
            case TEXT -> page.getByText(locator.value(), new Page.GetByTextOptions().setExact(true));
            case ROLE -> page.getByRole(toAriaRole(locator.value()),
                    new Page.GetByRoleOptions().setName(locator.accessibleName()).setExact(true));
        };
    }

    @Override
    public void navigate(String url) {
        page.navigate(url);
    }

    @Override
    public void click(UiLocator locator) {
        convert(locator).click();
    }

    @Override
    public void fill(UiLocator locator, String value) {
        convert(locator).fill(value);
    }

    @Override
    public void clear(UiLocator locator) {
        convert(locator).clear();
    }

    @Override
    public String getText(UiLocator locator) {
        return convert(locator).innerText();
    }

    @Override
    public String getAttribute(UiLocator locator, String attributeName) {
        return convert(locator).getAttribute(attributeName);
    }

    @Override
    public boolean isVisible(UiLocator locator) {
        return convert(locator).isVisible();
    }

    @Override
    public boolean isEnabled(UiLocator locator) {
        return convert(locator).isEnabled();
    }

    @Override
    public void waitForVisible(UiLocator locator) {
        convert(locator).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    @Override
    public void selectByValue(UiLocator locator, String value) {
        convert(locator).selectOption(value);
    }

    @Override
    public void press(UiLocator locator, String key) {
        convert(locator).press(key);
    }

    @Override
    public String getTitle() {
        return page.title();
    }

    @Override
    public String getCurrentUrl() {
        return page.url();
    }

    @Override
    public byte[] takeScreenshot() {
        return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    @Override
    public void saveScreenshot(Path destination) {
        page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(true));
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            try {
                browser.close();
            } finally {
                playwright.close();
            }
        }
    }

    private AriaRole toAriaRole(String value) {
        return AriaRole.valueOf(value.trim().replace('-', '_').toUpperCase());
    }

    private String cssQuoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String cssEscape(String value) {
        return value.replaceAll("([^a-zA-Z0-9_-])", "\\\\$1");
    }
}
