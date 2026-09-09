# Validation notes

## Sample website checked

Target:

`https://practicetestautomation.com/practice-test-login/`

The live page was checked on 2026-09-09. Its published practice cases currently specify:

- valid username: `student`
- valid password: `Password123`
- successful login URL contains `/logged-in-successfully/`
- invalid username message: `Your username is invalid!`
- invalid password message: `Your password is invalid!`

The login selectors used by the sample are based on the current page structure and corroborated examples for this exact practice page:

- username: `#username`
- password: `#password`
- submit: `#submit`

## Local static checks completed

- Java source parse: **49 files, 0 syntax errors**
- `pom.xml`: XML parse passed
- `testng.xml`: XML parse passed
- `log4j2.xml`: XML parse passed
- Excel workbook ZIP structure: passed
- Excel data range verified as `Login!A1:F4`
- stale SauceDemo imports/references removed from source and documentation

## Dependency availability checked

The selected Selenium `4.45.0`, Playwright Java `1.61.0`, Allure Java `2.35.3`, and Allure Maven plugin `3.0.2` artifacts are published in Maven Central.

## What could not be executed here

A full `mvn clean test` was not run in this container because Maven is not installed, and the container runtime cannot reach the public test site directly. The live site itself was inspected through the available web access, but that is not the same as executing the Java browser tests.

For the first local proof, run:

```powershell
mvn clean test -Ddata.source=feature -Dengine=selenium -Dbrowser=chrome -Dheadless=false -Dparallel.mode=none
```

You should see Chrome open the Practice Test Automation login page and execute one successful and two negative login scenarios.


## Java 8 conversion

The source is now written using Java 8 language/API constructs. The Maven
compiler is configured with source/target 1.8. Selenium is pinned to 4.13.0
and TestNG to 7.5.1 because later releases require newer Java runtimes.
