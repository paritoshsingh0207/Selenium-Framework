# Playwright Java BDD Framework

A lean but production-style Playwright + Java + Cucumber BDD + TestNG framework.

## Structure

- BaseTest: shared Playwright Page access
- BrowserManager: manages Playwright, Browser, BrowserContext and Page; Chromium, Firefox and WebKit supported
- Hooks: scenario setup/teardown and failure screenshots
- CommonActions: reusable click, sendText, dropdown, radio/checkbox, hover and screenshots
- Page Objects: selectors and business-level page actions
- Step Definitions: readable BDD glue only
- TestRunner: Cucumber + TestNG execution
- Allure: standard Cucumber Allure reporting
- Log4j2: console and rolling file logging

## Run

Install Chromium once if required:

```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Run tests:

```bash
mvn clean test
```

Headless:

```bash
mvn clean test -Dheadless=true
```

Allure:

```bash
mvn allure:serve
```

Default settings are in `src/test/resources/config.properties`.
