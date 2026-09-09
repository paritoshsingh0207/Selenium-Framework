package com.hybrid.tests.model;

/** Test data shared by feature-file and Excel-driven login execution. */
public final class LoginData {
    private final String testCaseId;
    private final String username;
    private final String password;
    private final String expectedResult;
    private final String expectedMessage;

    public LoginData(String testCaseId, String username, String password,
                     String expectedResult, String expectedMessage) {
        this.testCaseId = testCaseId;
        this.username = username;
        this.password = password;
        this.expectedResult = expectedResult;
        this.expectedMessage = expectedMessage;
    }

    public String testCaseId() { return testCaseId; }
    public String username() { return username; }
    public String password() { return password; }
    public String expectedResult() { return expectedResult; }
    public String expectedMessage() { return expectedMessage; }
}
