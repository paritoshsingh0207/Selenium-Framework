# Selenium Java BDD Framework

A lean but production-style Selenium + Java + Cucumber BDD + TestNG framework.

## Structure

- BaseTest: shared WebDriver access
- DriverManager: creates and manages WebDriver; Chrome, Firefox and Edge supported
- Hooks: scenario setup/teardown and failure screenshots
- CommonActions: reusable click, sendText, dropdown, radio/checkbox, hover, waits and screenshots
- Page Objects: locators and business-level page actions
- Step Definitions: readable BDD glue only
- TestRunner: Cucumber + TestNG execution
- Allure: standard Cucumber Allure reporting
- Log4j2: console and rolling file logging

## Run

```bash
mvn clean test
```

Headless:

```bash
mvn clean test -Dheadless=true
```

Choose a browser:

```bash
mvn clean test -Dbrowser=firefox
mvn clean test -Dbrowser=edge
```

Allure:

```bash
mvn allure:serve
```

Default settings are in `src/test/resources/config.properties`.
