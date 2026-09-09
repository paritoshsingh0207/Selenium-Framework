package com.hybrid.tests.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * TestNG runner for feature-file data.
 *
 * Keep the glue packages explicit. When a new application area is added,
 * its step-definition package is easy to find from here.
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {
                "com.hybrid.tests.stepdefinitions",
                "com.hybrid.tests.hooks"
        },
        tags = "@feature-data",
        plugin = {
                "pretty",
                "summary",
                "io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm"
        },
        monochrome = true)
public final class CucumberTestRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
