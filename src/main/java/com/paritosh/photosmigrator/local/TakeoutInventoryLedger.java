package com.paritosh.photosmigrator.local;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                appendAudit(workbook, "INVENTORY_CREATED", "Items=" + items.size());
                workbook.write(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write local Excel ledger: " + target, e);
        }
    }

    public List<TakeoutMediaItem> readItems(Path ledger) {
        if (ledger == null || !Files.isRegularFile(ledger)) {
            throw new IllegalArgumentException("Ledger does not exist: " + ledger);
        }
        try (InputStream in = Files.newInputStream(ledger); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheet("Items");
            if (sheet == null) throw new IllegalStateException("Ledger is missing Items sheet");
            List<TakeoutMediaItem> items = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || text(row.getCell(0)).isBlank()) continue;
                String taken = text(row.getCell(7));
                items.add(new TakeoutMediaItem(
                        text(row.getCell(0)),
                        text(row.getCell(1)),
                        text(row.getCell(2)),
                        text(row.getCell(3)),
                        text(row.getCell(4)),
                        (long) numeric(row.getCell(5)),
                        text(row.getCell(6)),
                        taken.isBlank() ? null : Instant.parse(taken),
                        bool(row.getCell(8)),
                        text(row.getCell(9))
                ));
            }
            return List.copyOf(items);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read local Excel ledger: " + ledger, e);
        }
    }

    public void applyAllocation(Path ledger, AllocationResult allocation) {
        Path absolute = ledger.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) throw new IllegalArgumentException("Ledger does not exist: " + absolute);
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (InputStream in = Files.newInputStream(absolute); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Map<String, AllocationResult.Assignment> byItem = new HashMap<>();
            for (AllocationResult.Assignment assignment : allocation.assignments()) {
                byItem.put(assignment.itemId(), assignment);
            }
            var unassigned = new java.util.HashSet<>(allocation.unassignedItemIds());

            Sheet items = workbook.getSheet("Items");
            if (items == null) throw new IllegalStateException("Ledger is missing Items sheet");
            for (int i = 1; i <= items.getLastRowNum(); i++) {
                Row row = items.getRow(i);
                if (row == null) continue;
                String id = text(row.getCell(0));
                if (id.isBlank() || bool(row.getCell(8))) continue;
                AllocationResult.Assignment assignment = byItem.get(id);
                if (assignment != null) {
                    row.createCell(11).setCellValue(assignment.accountLabel());
                    row.createCell(10).setCellValue("PLANNED");
                } else if (unassigned.contains(id)) {
                    row.createCell(10).setCellValue("UNASSIGNED_CAPACITY");
                    row.createCell(13).setCellValue("No configured destination has enough remaining capacity");
                }
            }

            Sheet destinations = workbook.getSheet("Destinations");
            if (destinations == null) destinations = workbook.createSheet("Destinations");
            clearRowsAfterHeader(destinations);
            int rowNum = 1;
            for (Map.Entry<String, Long> entry : allocation.maxBytes().entrySet()) {
                Row row = destinations.createRow(rowNum++);
                long assigned = allocation.assignedBytes().getOrDefault(entry.getKey(), 0L);
                row.createCell(0).setCellValue(entry.getKey());
                row.createCell(1).setCellValue(entry.getValue());
                row.createCell(2).setCellValue(assigned);
                row.createCell(3).setCellValue(assigned <= entry.getValue() ? "PLANNED" : "OVER_CAPACITY");
            }
            appendAudit(workbook, "ALLOCATION_PLANNED",
                    "Assigned=" + allocation.assignments().size() + ", Unassigned=" + allocation.unassignedItemIds().size());

            try (OutputStream out = Files.newOutputStream(temp)) {
                workbook.write(out);
            }
            replaceAtomically(temp, absolute);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to apply destination allocation to ledger: " + absolute, e);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
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
        if (rowNum > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));
        }
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

    private void appendAudit(XSSFWorkbook workbook, String event, String details) {
        Sheet sheet = workbook.getSheet("Audit");
        if (sheet == null) {
            sheet = workbook.createSheet("Audit");
            header(sheet, "Timestamp UTC", "Event", "Details");
        }
        int rowNum = Math.max(1, sheet.getLastRowNum() + 1);
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(Instant.now().toString());
        row.createCell(1).setCellValue(event);
        row.createCell(2).setCellValue(details);
    }

    private void clearRowsAfterHeader(Sheet sheet) {
        for (int i = sheet.getLastRowNum(); i >= 1; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
    }

    private String text(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> Long.toString((long) cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private double numeric(Cell cell) {
        if (cell == null) return 0;
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                ? cell.getNumericCellValue()
                : Double.parseDouble(text(cell));
    }

    private boolean bool(Cell cell) {
        if (cell == null) return false;
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BOOLEAN
                ? cell.getBooleanCellValue()
                : Boolean.parseBoolean(text(cell));
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void header(Sheet sheet, String... headers) {
        Row row = sheet.getRow(0) == null ? sheet.createRow(0) : sheet.getRow(0);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    private void row(Sheet sheet, int index, String key, String value) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }
}
