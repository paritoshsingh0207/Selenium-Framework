package com.hybrid.framework.retry;

import org.testng.ITestResult;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RetryTracker {
    private static final ConcurrentMap<String, AtomicInteger> RETRIES = new ConcurrentHashMap<>();

    private RetryTracker() {
    }

    public static int retriesUsed(ITestResult result) {
        AtomicInteger value = RETRIES.get(key(result));
        return value == null ? 0 : value.get();
    }

    public static int currentAttempt(ITestResult result) {
        return retriesUsed(result) + 1;
    }

    public static int increment(ITestResult result) {
        return RETRIES.computeIfAbsent(key(result), ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static void clear(ITestResult result) {
        RETRIES.remove(key(result));
    }

    private static String key(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName()
                + "#" + Arrays.deepToString(result.getParameters());
    }
}
