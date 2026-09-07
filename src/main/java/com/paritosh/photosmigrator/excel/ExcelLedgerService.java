package com.paritosh.photosmigrator.excel;

import com.paritosh.photosmigrator.model.MigrationItem;
import com.paritosh.photosmigrator.model.MigrationStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelLedgerService {
    private static final String ITEMS = "Items";
    private static final String SUMMARY = "Summary";
    private static final String FAILURES = "Failures";
    private static final String AUDIT = "Audit";
    private static final String CONFIG = "Config";

    public byte[] createEmptyWorkbook(String migrationId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet(SUMMARY);
            summary.createRow(0).createCell(0).setCellValue("Migration ID");
            summary.getRow(0).createCell(1).setCellValue(migrationId);
            summary.createRow(1).createCell(0).setCellValue("Created UTC");
            summary.getRow(1).createCell(1).setCellValue(Instant.now().toString());

            Sheet items = workbook.createSheet(ITEMS);
            String[] headers = {"ID", "Filename", "MIME Type", "Size Bytes", "SHA-256", "Status",
                    "Attempts", "Source Ref", "Destination Ref", "Last Updated UTC", "Error"};
            Row header = items.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            workbook.createSheet(FAILURES);
            workbook.createSheet(AUDIT);
            workbook.createSheet(CONFIG);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Excel ledger", e);
        }
    }

    public List<MigrationItem> readItems(byte[] workbookBytes) {
        if (workbookBytes == null || workbookBytes.length == 0) {
            return List.of();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheet(ITEMS);
            if (sheet == null) return List.of();
            List<MigrationItem> items = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;
                items.add(fromRow(row));
            }
            return items;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Excel ledger", e);
        }
    }

    public byte[] upsertItem(byte[] workbookBytes, String migrationId, MigrationItem item) {
        byte[] source = workbookBytes == null || workbookBytes.length == 0
                ? createEmptyWorkbook(migrationId)
                : workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet(ITEMS);
            int targetRow = findRow(sheet, item.id());
            Row row = targetRow >= 0 ? sheet.getRow(targetRow) : sheet.createRow(sheet.getLastRowNum() + 1);
            writeRow(row, item);
            appendAudit(workbook, item);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to update Excel ledger", e);
        }
    }

    private int findRow(Sheet sheet, String id) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(0) != null && id.equals(row.getCell(0).getStringCellValue())) {
                return i;
            }
        }
        return -1;
    }

    private void writeRow(Row row, MigrationItem item) {
        row.createCell(0).setCellValue(item.id());
        row.createCell(1).setCellValue(item.fileName());
        row.createCell(2).setCellValue(item.mimeType());
        row.createCell(3).setCellValue(item.sizeBytes());
        row.createCell(4).setCellValue(nullToEmpty(item.sha256()));
        row.createCell(5).setCellValue(item.status().name());
        row.createCell(6).setCellValue(item.attempts());
        row.createCell(7).setCellValue(nullToEmpty(item.sourceRef()));
        row.createCell(8).setCellValue(nullToEmpty(item.destinationRef()));
        row.createCell(9).setCellValue(item.lastUpdated() == null ? Instant.now().toString() : item.lastUpdated().toString());
        row.createCell(10).setCellValue(nullToEmpty(item.error()));
    }

    private MigrationItem fromRow(Row row) {
        return new MigrationItem(
                row.getCell(0).getStringCellValue(),
                row.getCell(1).getStringCellValue(),
                row.getCell(2).getStringCellValue(),
                (long) row.getCell(3).getNumericCellValue(),
                row.getCell(4).getStringCellValue(),
                MigrationStatus.valueOf(row.getCell(5).getStringCellValue()),
                (int) row.getCell(6).getNumericCellValue(),
                row.getCell(7).getStringCellValue(),
                row.getCell(8).getStringCellValue(),
                Instant.parse(row.getCell(9).getStringCellValue()),
                row.getCell(10).getStringCellValue()
        );
    }

    private void appendAudit(XSSFWorkbook workbook, MigrationItem item) {
        Sheet audit = workbook.getSheet(AUDIT);
        int rowNumber = audit.getLastRowNum() + 1;
        Row row = audit.createRow(rowNumber);
        row.createCell(0).setCellValue(Instant.now().toString());
        row.createCell(1).setCellValue(item.id());
        row.createCell(2).setCellValue(item.status().name());
        row.createCell(3).setCellValue(nullToEmpty(item.error()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
