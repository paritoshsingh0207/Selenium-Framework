@feature-data @login
Feature: Practice Test Automation login
  The same browser flow can be executed with Selenium or Playwright.
  Values written in this feature file are passed through the Cucumber step definitions.

  Background:
    Given the user opens the Practice Test Automation login page

  @positive
  Scenario: Student can log in with valid credentials
    When the user enters username "student"
    And the user enters password "Password123"
    And the user clicks the Submit button
    Then the login should be successful

  @negative
  Scenario Outline: Invalid credentials show the expected message
    When the user enters username "<username>"
    And the user enters password "<password>"
    And the user clicks the Submit button
    Then the login should fail with message "<expectedMessage>"

    Examples:
      | username      | password          | expectedMessage            |
      | incorrectUser | Password123       | Your username is invalid!  |
      | student       | incorrectPassword | Your password is invalid!  |
