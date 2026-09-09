package com.hybrid.framework.base;

import com.hybrid.framework.artifacts.ArtifactContext;
import com.hybrid.framework.artifacts.ScreenshotManager;
import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.DriverContext;
import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiDriverFactory;
import com.hybrid.framework.logging.TestLogContext;
import com.hybrid.framework.reporting.AllureSupport;
import com.hybrid.framework.retry.RetryTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.nio.file.Path;

public abstract class BaseTest {
    private static final Logger LOGGER = LogManager.getLogger(BaseTest.class);

    @BeforeMethod(alwaysRun = true)
    public void setUp(ITestResult result) {
        TestLogContext.bind(result);
        UiDriver driver = UiDriverFactory.create(FrameworkConfig.engine(), FrameworkConfig.browser(),
                FrameworkConfig.headless(), FrameworkConfig.timeout());
        DriverContext.set(driver);
        LOGGER.info("BROWSER_SETUP_SUCCESS engine={} browser={} thread={}",
                FrameworkConfig.engine(), FrameworkConfig.browser(), Thread.currentThread().getName());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        UiDriver driver = DriverContext.getOrNull();
        try {
            if (!result.isSuccess() && driver != null && FrameworkConfig.screenshotOnFailure()) {
                Path path = ScreenshotManager.capture(result.getTestClass().getRealClass().getSimpleName(),
                        result.getMethod().getMethodName(), RetryTracker.currentAttempt(result), driver);
                ArtifactContext.setScreenshot(path);
                AllureSupport.attachScreenshot("Failure screenshot", path);
            }
        } finally {
            if (driver != null) {
                try {
                    driver.close();
                } finally {
                    DriverContext.unload();
                }
            }
        }
    }

    protected UiDriver driver() {
        return DriverContext.get();
    }
}
