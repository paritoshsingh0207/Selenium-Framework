package com.paritosh.photosmigrator.local;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class TakeoutInventoryLedger {
    public void write(Path target, List<TakeoutMediaItem> items) {
        try {
            Path parent = target.toAbsolutePath().normalize().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (XSSFWorkbook workbook = new XSSFWorkbook();
                 OutputStream out = Files.newOutputStream(target)) {
                writeSummary(workbook, items);
                writeItems(workbook, items);
                writeDuplicates(workbook, items);
                writeBlankSheet(workbook, "Failures", "Timestamp UTC", "Source Ref", "Error");
                writeBlankSheet(workbook, "Destinations", "Account", "Max Bytes", "Assigned Bytes", "Status");
                writeBlankSheet(workbook, "Cleanup Plan", "Batch", "From", "To", "Destination", "Expected", "Verified", "Status");
                writeBlankSheet(workbook, "Audit", "Timestamp UTC", "Event", "Details");
                workbook.write(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write local Excel ledger: " + target, e);
        }
    }

    private void writeSummary(XSSFWorkbook workbook, List<TakeoutMediaItem> items) {
        Sheet sheet = workbook.createSheet("Summary");
        long totalBytes = items.stream().mapToLong(TakeoutMediaItem::sizeBytes).sum();
        long uniqueBytes = items.stream().filter(item -> !item.duplicate()).mapToLong(TakeoutMediaItem::sizeBytes).sum();
        long duplicates = items.stream().filter(TakeoutMediaItem::duplicate).count();

        row(sheet, 0, "Created UTC", Instant.now().toString());
        row(sheet, 1, "Media entries", Long.toString(items.size()));
        row(sheet, 2, "Duplicate entries", Long.toString(duplicates));
        row(sheet, 3, "Total bytes", Long.toString(totalBytes));
        row(sheet, 4, "Unique bytes", Long.toString(uniqueBytes));
        row(sheet, 5, "Unique GiB", String.format("%.3f", uniqueBytes / 1024d / 1024d / 1024d));
        row(sheet, 6, "Policy", "DO NOT DELETE SOURCE until destination verification is complete");
    }

    private void writeItems(XSSFWorkbook workbook, List<TakeoutMediaItem> items) {
        Sheet sheet = workbook.createSheet("Items");
        String[] headers = {"ID", "Archive", "Entry", "Filename", "MIME Type", "Size Bytes", "SHA-256",
                "Taken Time UTC", "Duplicate", "Duplicate Of", "Status", "Destination Account", "Destination Ref", "Error"};
        header(sheet, headers);
        int rowNum = 1;
        for (TakeoutMediaItem item : items) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.id());
            row.createCell(1).setCellValue(item.archivePath());
            row.createCell(2).setCellValue(item.entryName());
            row.createCell(3).setCellValue(item.fileName());
            row.createCell(4).setCellValue(item.mimeType());
            row.createCell(5).setCellValue(item.sizeBytes());
            row.createCell(6).setCellValue(item.sha256());
            row.createCell(7).setCellValue(item.takenTime() == null ? "" : item.takenTime().toString());
            row.createCell(8).setCellValue(item.duplicate());
            row.createCell(9).setCellValue(item.duplicateOf());
            row.createCell(10).setCellValue(item.duplicate() ? "SKIPPED_DUPLICATE" : "HASHED");
            row.createCell(11).setCellValue("");
            row.createCell(12).setCellValue("");
            row.createCell(13).setCellValue("");
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(1, rowNum - 1), 0, headers.length - 1));
    }

    private void writeDuplicates(XSSFWorkbook workbook, List<TakeoutMediaItem> items) {
        Sheet sheet = workbook.createSheet("Duplicates");
        String[] headers = {"ID", "Source Ref", "SHA-256", "Size Bytes", "Duplicate Of"};
        header(sheet, headers);
        int rowNum = 1;
        for (TakeoutMediaItem item : items) {
            if (!item.duplicate()) continue;
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.id());
            row.createCell(1).setCellValue(item.sourceRef());
            row.createCell(2).setCellValue(item.sha256());
            row.createCell(3).setCellValue(item.sizeBytes());
            row.createCell(4).setCellValue(item.duplicateOf());
        }
    }

    private void writeBlankSheet(XSSFWorkbook workbook, String name, String... headers) {
        Sheet sheet = workbook.createSheet(name);
        header(sheet, headers);
    }

    private void header(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    private void row(Sheet sheet, int index, String key, String value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }
}
