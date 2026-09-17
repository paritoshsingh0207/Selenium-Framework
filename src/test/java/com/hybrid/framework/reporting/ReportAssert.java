package com.hybrid.framework.reporting;

import org.testng.Assert;

/**
 * Assertion wrapper that records expected and actual values before delegating
 * to TestNG. This keeps the PDF evidence complete without changing assertion
 * semantics or hiding failures.
 */
public final class ReportAssert {
    private ReportAssert() {
    }

    public static void assertTrue(String description, boolean condition,
                                  String expected, String actual) {
        ReportEvidenceContext.assertion(
                description,
                condition,
                expected,
                actual,
                condition ? "Assertion matched the expected condition"
                        : "Assertion did not match the expected condition");

        Assert.assertTrue(condition,
                description + " | expected=" + expected + " | actual=" + actual);
    }

    public static void assertEquals(String description, String actual, String expected) {
        boolean passed = expected == null ? actual == null : expected.equals(actual);

        ReportEvidenceContext.assertion(
                description,
                passed,
                String.valueOf(expected),
                String.valueOf(actual),
                passed ? "Actual value matched expected value"
                        : "Actual value did not match expected value");

        Assert.assertEquals(actual, expected, description);
    }
}
