package com.framework.hooks;

import com.framework.driver.BrowserManager;
import com.framework.utils.CommonActions;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Hooks {
    private static final Logger LOGGER = LogManager.getLogger(Hooks.class);
    private final CommonActions actions = new CommonActions();

    @Before
    public void setUp(Scenario scenario) {
        LOGGER.info("========== START SCENARIO: {} ==========", scenario.getName());
        BrowserManager.startBrowser();
    }

    @After
    public void tearDown(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                LOGGER.error("Scenario FAILED: {}", scenario.getName());

                if (BrowserManager.hasPage()) {
                    byte[] screenshot = actions.takeScreenshot();
                    if (screenshot != null && screenshot.length > 0) {
                        scenario.attach(screenshot, "image/png", "Failure Screenshot");
                        LOGGER.info("Failure screenshot attached to scenario");
                    } else {
                        LOGGER.warn("Failure screenshot was skipped; original scenario error is preserved");
                    }
                }
            } else {
                LOGGER.info("Scenario PASSED: {}", scenario.getName());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Teardown attachment step failed, preserving original scenario result: {}", exception.getMessage());
        } finally {
            BrowserManager.closeBrowser();
            LOGGER.info("========== END SCENARIO: {} ==========", scenario.getName());
        }
    }
}
