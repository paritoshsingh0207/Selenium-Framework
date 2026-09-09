package com.hybrid.framework.reporting;

import org.testng.ITestResult;

public enum ExecutionStatus {
    PASSED, FAILED, SKIPPED, UNKNOWN;

    public static ExecutionStatus from(int status) {
        return switch (status) {
            case ITestResult.SUCCESS -> PASSED;
            case ITestResult.FAILURE -> FAILED;
            case ITestResult.SKIP -> SKIPPED;
            default -> UNKNOWN;
        };
    }
}
