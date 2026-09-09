package com.hybrid.framework.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExcelReader {
    private static final Logger LOGGER = LogManager.getLogger(ExcelReader.class);

    private ExcelReader() {
    }

    public static List<ExcelRow> read(String location, String sheetName, int headerRowIndex,
                                      int dataStartRowIndex, String[] requiredColumns) {
        try (ResolvedInput input = resolve(location);
             Workbook workbook = WorkbookFactory.create(input.stream())) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new ExcelDataException("Sheet '" + sheetName + "' not found");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.getDefault());
            Map<Integer, String> headers = headers(sheet, headerRowIndex, formatter, evaluator);
            validateRequired(headers, requiredColumns);
            int firstDataRow = dataStartRowIndex >= 0 ? dataStartRowIndex : headerRowIndex + 1;
            List<ExcelRow> result = new ArrayList<>();
            for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasData = false;
                for (Map.Entry<Integer, String> header : headers.entrySet()) {
                    Cell cell = row.getCell(header.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
                    if (!value.isBlank()) hasData = true;
                    values.put(header.getValue(), value);
                }
                if (hasData) {
                    result.add(new ExcelRow(input.description(), sheetName, rowIndex + 1, values));
                }
            }
            LOGGER.info("EXCEL_DATA_LOADED source={} sheet={} rows={}", input.description(), sheetName, result.size());
            return List.copyOf(result);
        } catch (ExcelDataException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExcelDataException("Unable to read Excel file " + location + " sheet " + sheetName, exception);
        }
    }

    private static Map<Integer, String> headers(Sheet sheet, int rowIndex, DataFormatter formatter,
                                                 FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) throw new ExcelDataException("Header row " + (rowIndex + 1) + " is missing");
        Map<Integer, String> headers = new LinkedHashMap<>();
        Set<String> normalized = new HashSet<>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
            if (value.isBlank()) continue;
            if (!normalized.add(value.toLowerCase(Locale.ROOT))) {
                throw new ExcelDataException("Duplicate Excel header: " + value);
            }
            headers.put(column, value);
        }
        if (headers.isEmpty()) throw new ExcelDataException("No Excel headers found");
        return headers;
    }

    private static void validateRequired(Map<Integer, String> headers, String[] required) {
        if (required == null) return;
        Set<String> available = new HashSet<>();
        headers.values().forEach(value -> available.add(value.toLowerCase(Locale.ROOT)));
        List<String> missing = new ArrayList<>();
        for (String value : required) {
            if (value != null && !value.isBlank() && !available.contains(value.trim().toLowerCase(Locale.ROOT))) {
                missing.add(value);
            }
        }
        if (!missing.isEmpty()) throw new ExcelDataException("Missing Excel columns: " + missing);
    }

    private static ResolvedInput resolve(String location) throws Exception {
        Path path = Paths.get(location);
        if (Files.isRegularFile(path)) {
            return new ResolvedInput(Files.newInputStream(path), path.toAbsolutePath().toString());
        }
        String resource = location.startsWith("/") ? location.substring(1) : location;
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new ExcelDataException("Excel file not found: " + location);
        return new ResolvedInput(stream, "classpath:" + resource);
    }

    private record ResolvedInput(InputStream stream, String description) implements AutoCloseable {
        @Override
        public void close() throws Exception { stream.close(); }
    }
}
