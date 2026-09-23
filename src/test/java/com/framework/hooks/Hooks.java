package com.framework.hooks;

import com.framework.driver.DriverManager;
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
        DriverManager.startDriver();
    }

    @After
    public void tearDown(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                LOGGER.error("Scenario FAILED: {}", scenario.getName());
                if (DriverManager.hasDriver()) {
                    scenario.attach(actions.takeScreenshot(), "image/png", "Failure Screenshot");
                    LOGGER.info("Failure screenshot attached to scenario");
                }
            } else {
                LOGGER.info("Scenario PASSED: {}", scenario.getName());
            }
        } finally {
            DriverManager.quitDriver();
            LOGGER.info("========== END SCENARIO: {} ==========", scenario.getName());
        }
    }
}
