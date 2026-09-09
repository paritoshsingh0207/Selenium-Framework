package com.hybrid.framework.listeners;

import com.hybrid.framework.artifacts.ArtifactContext;
import com.hybrid.framework.logging.TestLogContext;
import com.hybrid.framework.reporting.ExecutionResultStore;
import com.hybrid.framework.retry.RetryTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestListener;
import org.testng.ITestResult;

public final class TestExecutionListener implements ITestListener {
    private static final Logger LOGGER = LogManager.getLogger(TestExecutionListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        ArtifactContext.clear();
        TestLogContext.bind(result);
        LOGGER.info("TEST_START method={} attempt={} parameters={}",
                result.getMethod().getMethodName(), RetryTracker.currentAttempt(result), result.getParameters());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, "TEST_SUCCESS");
        RetryTracker.clear(result);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        finish(result, "TEST_FAILURE");
        // RetryAnalyzer clears rejected/exhausted failures; successful retries clear in onTestSuccess.
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        finish(result, "TEST_SKIPPED");
    }

    private void finish(ITestResult result, String event) {
        ExecutionResultStore.record(result, ArtifactContext.screenshot());
        LOGGER.info("{} method={} attempt={} durationMs={} screenshot={}", event,
                result.getMethod().getMethodName(), RetryTracker.currentAttempt(result),
                result.getEndMillis() - result.getStartMillis(), ArtifactContext.screenshot());
        ArtifactContext.clear();
        TestLogContext.clear();
    }
}
