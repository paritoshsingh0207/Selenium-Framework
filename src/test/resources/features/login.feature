Feature: Login

  Scenario: Successful login
    Given user opens the login page
    When user enters username "student"
    And user enters password "Password123"
    And user clicks the submit button
    Then successful login page should be displayed
