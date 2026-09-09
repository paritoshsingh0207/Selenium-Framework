package com.hybrid.framework.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.DataProvider;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ExcelDataProvider {
    private static final Logger LOGGER = LogManager.getLogger(ExcelDataProvider.class);
    private static final Set<String> ACTIVE = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("y", "yes", "true", "1", "run")));

    private ExcelDataProvider() {
    }

    @DataProvider(name = "excelData", parallel = true)
    public static Object[][] provide(Method method) {
        ExcelSource source = method.getAnnotation(ExcelSource.class);
        if (source == null) {
            throw new ExcelDataException(method + " must declare @ExcelSource");
        }
        String file = System.getProperty("excel.file", source.file());
        String sheet = System.getProperty("excel.sheet", source.sheet());
        List<ExcelRow> rows = ExcelReader.read(file, sheet, source.headerRow(), source.dataStartRow(),
                source.requiredColumns());
        boolean filter = Boolean.parseBoolean(System.getProperty("excel.filterRunMode",
                String.valueOf(source.filterByRunMode())));
        if (filter) {
            rows = rows.stream().filter(row -> ACTIVE.contains(
                    row.get(source.runModeColumn()).trim().toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (rows.isEmpty()) {
            throw new ExcelDataException("No executable Excel rows found for " + method.getName());
        }
        LOGGER.info("EXCEL_PROVIDER_READY test={} rows={} file={} sheet={}", method.getName(), rows.size(), file, sheet);
        return rows.stream().map(row -> new Object[]{row}).toArray(Object[][]::new);
    }
}
