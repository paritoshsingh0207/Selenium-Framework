package com.hybrid.framework.logging;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.retry.RetryTracker;
import org.apache.logging.log4j.ThreadContext;
import org.testng.ITestResult;

public final class TestLogContext {
    private TestLogContext() {
    }

    public static void bind(ITestResult result) {
        ThreadContext.put("testName", result.getTestClass().getName() + "." + result.getMethod().getMethodName());
        ThreadContext.put("attempt", String.valueOf(RetryTracker.currentAttempt(result)));
        ThreadContext.put("engine", FrameworkConfig.engine().name());
        ThreadContext.put("browser", FrameworkConfig.browser().name());
        ThreadContext.put("dataSource", FrameworkConfig.dataSource());
    }

    public static void scenario(String name) {
        ThreadContext.put("testName", name);
    }

    public static void clear() {
        ThreadContext.clearAll();
    }
}
