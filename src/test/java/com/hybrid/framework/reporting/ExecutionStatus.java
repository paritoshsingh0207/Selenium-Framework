package com.hybrid.framework.reporting;

import org.testng.ITestResult;

public enum ExecutionStatus {
    PASSED, FAILED, SKIPPED, UNKNOWN;

    public static ExecutionStatus from(int status) {
        switch (status) {
            case ITestResult.SUCCESS:
                return PASSED;
            case ITestResult.FAILURE:
                return FAILED;
            case ITestResult.SKIP:
                return SKIPPED;
            default:
                return UNKNOWN;
        }
    }
}
