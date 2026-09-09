package com.hybrid.framework.retry;

import com.hybrid.framework.config.FrameworkConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public final class RetryAnalyzer implements IRetryAnalyzer {
    private static final Logger LOGGER = LogManager.getLogger(RetryAnalyzer.class);

    @Override
    public boolean retry(ITestResult result) {
        int used = RetryTracker.retriesUsed(result);
        if (used >= FrameworkConfig.retryCount()) {
            LOGGER.error("RETRY_EXHAUSTED test={} attempts={}", name(result), used + 1);
            RetryTracker.clear(result);
            return false;
        }
        if (!RetryPolicy.shouldRetry(result.getThrowable())) {
            LOGGER.error("RETRY_REJECTED test={} failureType={}", name(result),
                    result.getThrowable() == null ? "<none>" : result.getThrowable().getClass().getName());
            RetryTracker.clear(result);
            return false;
        }
        int retry = RetryTracker.increment(result);
        result.setAttribute("retryScheduled", true);
        LOGGER.warn("RETRY_SCHEDULED test={} nextAttempt={} maximumAttempts={}",
                name(result), retry + 1, FrameworkConfig.retryCount() + 1);
        return true;
    }

    private String name(ITestResult result) {
        return result.getTestClass().getName() + "." + result.getMethod().getMethodName();
    }
}
