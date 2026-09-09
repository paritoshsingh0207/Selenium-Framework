package com.hybrid.tests.hooks;

import com.hybrid.framework.artifacts.ArtifactContext;
import com.hybrid.framework.artifacts.ScreenshotManager;
import com.hybrid.framework.config.FrameworkConfig;
import com.hybrid.framework.core.DriverContext;
import com.hybrid.framework.core.UiDriver;
import com.hybrid.framework.core.UiDriverFactory;
import com.hybrid.framework.logging.TestLogContext;
import com.hybrid.framework.reporting.AllureSupport;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

import java.nio.file.Path;

public final class CucumberHooks {
    @Before(order = 0)
    public void before(Scenario scenario) {
        TestLogContext.scenario(scenario.getName());
        DriverContext.set(UiDriverFactory.create(FrameworkConfig.engine(), FrameworkConfig.browser(),
                FrameworkConfig.headless(), FrameworkConfig.timeout()));
    }

    @After(order = 1000)
    public void after(Scenario scenario) {
        UiDriver driver = DriverContext.getOrNull();
        try {
            if (scenario.isFailed() && driver != null && FrameworkConfig.screenshotOnFailure()) {
                Path screenshot = ScreenshotManager.capture("Cucumber", scenario.getName(), 1, driver);
                ArtifactContext.setScreenshot(screenshot);
                byte[] bytes = java.nio.file.Files.readAllBytes(screenshot);
                scenario.attach(bytes, "image/png", "Failure screenshot");
                AllureSupport.attachScreenshot("Failure screenshot", screenshot);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to capture Cucumber failure screenshot", exception);
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
}
