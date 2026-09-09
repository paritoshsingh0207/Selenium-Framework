# Hybrid Selenium + Playwright Automation Framework

A Java 17 Maven framework where the same test flow can run with **Selenium** or **Playwright**, and test data can come from either a **Cucumber feature file** or **Excel**.

The working sample targets:

`https://practicetestautomation.com/practice-test-login/`

The site documents these practice cases: valid login with `student / Password123`, invalid username, and invalid password.

## What is included

- Selenium / Playwright selection at runtime
- Chrome, Edge, Firefox, Chromium, WebKit and Safari where supported by the selected engine
- Cucumber + TestNG
- Feature-file test data
- Excel test data (`.xlsx` / `.xls` reader through Apache POI)
- One shared login flow for both data sources
- Page Object Model
- Parallel execution
- Retry logic
- Failure screenshots
- Log4j2 execution logs
- Allure results/reporting
- PDF execution report
- Controlled self-healing with declared fallback locators

## Important folders

```text
src/main/java/com/hybrid/framework/        reusable framework code
src/test/java/com/hybrid/tests/pages/      website page objects
src/test/java/com/hybrid/tests/stepdefinitions/  Cucumber step definitions
src/test/java/com/hybrid/tests/scenarios/  shared browser flows
src/test/java/com/hybrid/tests/runners/    Cucumber runner
src/test/resources/features/               feature files
src/test/resources/testdata/               Excel test data
```

The actual step definitions are here:

`src/test/java/com/hybrid/tests/stepdefinitions/PracticeLoginSteps.java`

## Prerequisites

- JDK 17+
- Maven 3.9+
- Chrome / Edge / Firefox installed for Selenium runs
- Playwright browser binaries installed before Playwright runs

Check your local setup:

```bash
java -version
mvn -version
```

## First run: Selenium + Chrome + feature data

Use `headless=false` so you can watch the browser open, type the credentials and submit the form.

```bash
mvn clean test \
  -Ddata.source=feature \
  -Dengine=selenium \
  -Dbrowser=chrome \
  -Dheadless=false \
  -Dparallel.mode=none
```

Windows PowerShell can run the same command on one line:

```powershell
mvn clean test -Ddata.source=feature -Dengine=selenium -Dbrowser=chrome -Dheadless=false -Dparallel.mode=none
```

## Playwright setup and run

Install the Playwright browser binaries once:

```bash
mvn -q -DskipTests compile
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"
```

Then run:

```bash
mvn clean test \
  -Ddata.source=feature \
  -Dengine=playwright \
  -Dbrowser=chromium \
  -Dheadless=false \
  -Dparallel.mode=none
```

## Run with Excel data

The workbook is:

`src/test/resources/testdata/login-data.xlsx`

Run it through Selenium:

```bash
mvn clean test \
  -Ddata.source=excel \
  -Dengine=selenium \
  -Dbrowser=chrome \
  -Dheadless=false \
  -Dparallel.mode=none
```

Or through Playwright:

```bash
mvn clean test \
  -Ddata.source=excel \
  -Dengine=playwright \
  -Dbrowser=chromium \
  -Dheadless=false \
  -Dparallel.mode=none
```

## Run both data sources

```bash
mvn clean test -Ddata.source=both -Dengine=selenium -Dbrowser=chrome
```

This intentionally runs the feature scenarios and the Excel rows as separate test invocations while reusing the same page objects and `LoginScenarioExecutor`.

## Use another environment URL

The sample URL is the default, but it can be overridden:

```bash
mvn clean test -DbaseUrl="https://your-test-environment.example/login" ...
```

Changing only the URL does **not** make arbitrary website locators magically compatible. For a different application, add or update the page objects and feature steps under `src/test/java/com/hybrid/tests/` while keeping the framework layer unchanged.

## Parallel execution

```bash
mvn clean test \
  -Dparallel.mode=methods \
  -Dthread.count=4 \
  -Ddataprovider.thread.count=4
```

For the first visual run, use `-Dparallel.mode=none`. It is easier to follow one browser at a time.

## Retry

By default, transient browser/automation failures get one additional attempt. Assertion failures are not retried unless explicitly enabled.

```bash
mvn clean test -Dretry.count=2
mvn clean test -Dretry.count=1 -Dretry.assertions=true
mvn clean test -Dretry.count=0
```

## Self-healing

The framework does not invent locators or edit source code. It only tries fallback locators that were deliberately declared in the page object.

```bash
mvn clean test -Dself.healing.enabled=true
mvn clean test -Dself.healing.enabled=false
```

When a fallback is used, the event is written to the logs and reporting evidence.

## Reports and evidence

After execution:

- `artifacts/logs/` — detailed Log4j2 logs
- `artifacts/screenshots/` — failure screenshots
- `artifacts/self-healing/` — healing evidence
- `target/allure-results/` — Allure raw results
- `target/reports/hybrid-automation-report.pdf` — PDF report

Generate Allure HTML:

```bash
mvn allure:report
```

Open Allure locally:

```bash
mvn allure:serve
```

## Useful runtime properties

| Property | Default |
|---|---|
| `engine` | `selenium` |
| `browser` | `chrome` |
| `data.source` | `feature` |
| `baseUrl` | Practice Test Automation login URL |
| `headless` | `true` |
| `parallel.mode` | `methods` |
| `thread.count` | `4` |
| `dataprovider.thread.count` | `4` |
| `retry.count` | `1` |
| `self.healing.enabled` | `true` |

## Before adapting this to a real project

Do not keep real production credentials in Excel or feature files. Use environment variables or your CI/CD secret store for sensitive values.

See `docs/HOW_IT_WORKS.md` for the execution flow and where new application code should go.
