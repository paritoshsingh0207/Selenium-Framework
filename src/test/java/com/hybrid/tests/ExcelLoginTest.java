package com.hybrid.tests;

import com.hybrid.framework.base.BaseTest;
import com.hybrid.framework.data.ExcelDataProvider;
import com.hybrid.framework.data.ExcelRow;
import com.hybrid.framework.data.ExcelSource;
import com.hybrid.tests.model.LoginData;
import com.hybrid.tests.model.LoginDataMapper;
import com.hybrid.tests.scenarios.LoginScenarioExecutor;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import org.testng.annotations.Test;

/**
 * Excel entry point for the same login flow used by Cucumber.
 *
 * Nothing here knows how the page is automated. The row is mapped to
 * LoginData and handed to the shared scenario executor.
 */
@Feature("Authentication")
public final class ExcelLoginTest extends BaseTest {

    @Test(
            dataProvider = "excelData",
            dataProviderClass = ExcelDataProvider.class,
            groups = {"login", "excel-data"})
    @ExcelSource(
            file = "testdata/login-data.xlsx",
            sheet = "Login",
            requiredColumns = {
                    "TestCaseId",
                    "Username",
                    "Password",
                    "ExpectedResult",
                    "ExpectedMessage"
            },
            filterByRunMode = true)
    @Description("Runs the Practice Test Automation login flow with Excel test data")
    public void loginUsingExcel(ExcelRow row) {
        LoginData data = LoginDataMapper.fromExcel(row);
        new LoginScenarioExecutor(driver()).execute(data);
    }
}
