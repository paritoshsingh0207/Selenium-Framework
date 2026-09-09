# How the sample is wired

The repository has two different layers on purpose.

## Reusable framework layer

`src/main/java/com/hybrid/framework/`

This is the part that should survive when the application under test changes. It contains the driver abstraction, Selenium and Playwright adapters, logging, retry handling, screenshots, self-healing, Excel reading and reporting.

## Website-specific test layer

`src/test/java/com/hybrid/tests/`

This is where application knowledge belongs.

- `pages/PracticeLoginPage.java` — login-page locators and actions
- `pages/LoggedInPage.java` — successful-login checks
- `stepdefinitions/PracticeLoginSteps.java` — Cucumber step definitions
- `scenarios/LoginScenarioExecutor.java` — shared flow used by Feature and Excel data
- `ExcelLoginTest.java` — TestNG entry point for Excel rows
- `runners/CucumberTestRunner.java` — Cucumber/TestNG runner
- `hooks/CucumberHooks.java` — browser setup and cleanup for Cucumber

## Data route

Feature-file run:

`login.feature -> PracticeLoginSteps -> LoginScenarioExecutor -> page objects -> UiDriver -> Selenium/Playwright`

Excel run:

`login-data.xlsx -> ExcelDataProvider -> ExcelLoginTest -> LoginScenarioExecutor -> page objects -> UiDriver -> Selenium/Playwright`

Both routes deliberately meet at `LoginScenarioExecutor`. That keeps the actual browser flow consistent regardless of where the test data came from.
