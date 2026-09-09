package com.hybrid.tests.model;

public record LoginData(
        String testCaseId,
        String username,
        String password,
        String expectedResult,
        String expectedMessage) {
}
