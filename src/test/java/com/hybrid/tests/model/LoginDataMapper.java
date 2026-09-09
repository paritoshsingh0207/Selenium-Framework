package com.hybrid.tests.model;

import com.hybrid.framework.data.ExcelRow;

public final class LoginDataMapper {
    private LoginDataMapper() {
    }

    public static LoginData fromExcel(ExcelRow row) {
        return new LoginData(
                row.getRequiredString("TestCaseId"),
                row.getRequiredString("Username"),
                row.getRequiredString("Password"),
                row.getRequiredString("ExpectedResult"),
                row.get("ExpectedMessage"));
    }
}
