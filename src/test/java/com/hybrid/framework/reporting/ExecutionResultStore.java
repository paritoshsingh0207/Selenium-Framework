package com.hybrid.framework.reporting;

import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.data.ExcelRow;
import com.hybrid.framework.retry.RetryTracker;
import org.testng.ITestResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class ExecutionResultStore {
    private static final ConcurrentLinkedQueue<ExecutionRecord> RESULTS = new ConcurrentLinkedQueue<>();

    private ExecutionResultStore() {
    }

    public static void clear() { RESULTS.clear(); }

    public static void record(ITestResult result, Path screenshot) {
        String identity = parameterIdentity(result.getParameters());
        Throwable throwable = result.getThrowable();
        String message = throwable == null || throwable.getMessage() == null ? ""
                : throwable.getMessage().replace('\n', ' ').replace('\r', ' ');
        RESULTS.add(new ExecutionRecord(
                result.getTestClass().getName() + "#" + result.getMethod().getMethodName() + "#" + identity,
                result.getTestClass().getName() + "." + result.getMethod().getMethodName(),
                identity,
                ExecutionStatus.from(result.getStatus()),
                RetryTracker.currentAttempt(result),
                FrameworkConfig.engine().name(),
                FrameworkConfig.browser().name(),
                Thread.currentThread().getName(),
                result.getStartMillis(), result.getEndMillis(),
                Math.max(0, result.getEndMillis() - result.getStartMillis()),
                message,
                screenshot == null ? "" : screenshot.toAbsolutePath().toString()));
    }

    public static List<ExecutionRecord> snapshot() {
        List<ExecutionRecord> values = new ArrayList<>(RESULTS);
        values.sort(Comparator.comparingLong(ExecutionRecord::startTime));
        return List.copyOf(values);
    }

    private static String parameterIdentity(Object[] parameters) {
        if (parameters == null || parameters.length == 0) return "no-parameters";
        return Arrays.stream(parameters).map(parameter -> {
            if (parameter instanceof ExcelRow row) return row.executionId();
            return String.valueOf(parameter);
        }).collect(Collectors.joining("|"));
    }
}
