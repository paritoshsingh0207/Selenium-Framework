package com.paritosh.photosmigrator.local;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalMigrationLedger implements Closeable {
    private static final int COL_ID = 0;
    private static final int COL_ARCHIVE = 1;
    private static final int COL_ENTRY = 2;
    private static final int COL_FILENAME = 3;
    private static final int COL_MIME = 4;
    private static final int COL_SIZE = 5;
    private static final int COL_SHA = 6;
    private static final int COL_TAKEN = 7;
    private static final int COL_DUPLICATE = 8;
    private static final int COL_STATUS = 10;
    private static final int COL_DESTINATION_ACCOUNT = 11;
    private static final int COL_DESTINATION_REF = 12;
    private static final int COL_ERROR = 13;
    private static final int COL_ATTEMPTS = 14;

    private final Path ledgerPath;
    private final XSSFWorkbook workbook;
    private final Sheet items;
    private final Sheet audit;
    private final Sheet failures;
    private final Map<String, Integer> rowById = new LinkedHashMap<>();
    private final int checkpointEvery;
    private int dirtyUpdates;
    private boolean closed;

    public static LocalMigrationLedger open(Path ledgerPath) {
        return open(ledgerPath, 10);
    }

    public static LocalMigrationLedger open(Path ledgerPath, int checkpointEvery) {
        if (ledgerPath == null || !Files.isRegularFile(ledgerPath)) {
            throw new IllegalArgumentException("Ledger does not exist: " + ledgerPath);
        }
        if (checkpointEvery <= 0) throw new IllegalArgumentException("checkpointEvery must be greater than zero");
        try (InputStream in = Files.newInputStream(ledgerPath)) {
            return new LocalMigrationLedger(ledgerPath.toAbsolutePath().normalize(), new XSSFWorkbook(in), checkpointEvery);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open migration ledger: " + ledgerPath, e);
        }
    }

    private LocalMigrationLedger(Path ledgerPath, XSSFWorkbook workbook, int checkpointEvery) {
        this.ledgerPath = ledgerPath;
        this.workbook = workbook;
        this.checkpointEvery = checkpointEvery;
        this.items = requiredSheet("Items");
        this.audit = getOrCreateSheet("Audit", "Timestamp UTC", "Event", "Details");
        this.failures = getOrCreateSheet("Failures", "Timestamp UTC", "Item ID", "Source Ref", "Error");
        ensureAttemptsColumn();
        indexRows();
    }

    public List<LedgerMigrationRow> rows() {
        ensureOpen();
        List<LedgerMigrationRow> result = new ArrayList<>();
        for (int rowNumber = 1; rowNumber <= items.getLastRowNum(); rowNumber++) {
            Row row = items.getRow(rowNumber);
            if (row == null || text(row.getCell(COL_ID)).isBlank()) continue;
            result.add(readRow(row));
        }
        return List.copyOf(result);
    }

    public List<String> destinationAccounts() {
        Set<String> result = new LinkedHashSet<>();
        for (LedgerMigrationRow row : rows()) {
            if (!row.destinationAccount().isBlank() && !row.duplicate()) result.add(row.destinationAccount());
        }
        return List.copyOf(result);
    }

    public List<LedgerMigrationRow> workForDestination(String destination, int maxItems) {
        ensureOpen();
        if (maxItems <= 0) return List.of();
        List<LedgerMigrationRow> result = new ArrayList<>();
        for (LedgerMigrationRow row : rows()) {
            if (!destination.equalsIgnoreCase(row.destinationAccount()) || row.duplicate()) continue;
            if (isEligible(row.status())) {
                result.add(row);
                if (result.size() >= maxItems) break;
            }
        }
        return List.copyOf(result);
    }

    public void update(String itemId, String status, String destinationRef, String error,
                       boolean incrementAttempts, boolean forceCheckpoint) {
        ensureOpen();
        Integer rowNumber = rowById.get(itemId);
        if (rowNumber == null) throw new IllegalArgumentException("Unknown ledger item: " + itemId);
        Row row = items.getRow(rowNumber);
        int attempts = integer(row.getCell(COL_ATTEMPTS));
        if (incrementAttempts) attempts++;
        row.createCell(COL_STATUS).setCellValue(status);
        if (destinationRef != null) row.createCell(COL_DESTINATION_REF).setCellValue(destinationRef);
        row.createCell(COL_ERROR).setCellValue(error == null ? "" : error);
        row.createCell(COL_ATTEMPTS).setCellValue(attempts);
        appendAudit(itemId, status, error == null ? "" : error);
        if (status.startsWith("FAILED")) appendFailure(row, error);
        dirtyUpdates++;
        if (forceCheckpoint || dirtyUpdates >= checkpointEvery) checkpoint();
    }

    public void checkpoint() {
        ensureOpen();
        if (dirtyUpdates == 0) return;
        Path temp = ledgerPath.resolveSibling(ledgerPath.getFileName() + ".checkpoint.tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                workbook.write(out);
            }
            try {
                Files.move(temp, ledgerPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, ledgerPath, StandardCopyOption.REPLACE_EXISTING);
            }
            dirtyUpdates = 0;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to checkpoint migration ledger: " + ledgerPath, e);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    public long countStatus(String status) {
        return rows().stream().filter(row -> status.equals(row.status())).count();
    }

    private boolean isEligible(String status) {
        return switch (status) {
            case "PLANNED", "FAILED_RETRYABLE", "UPLOADING", "UPLOADED", "WAITING_DESTINATION_READY" -> true;
            default -> false;
        };
    }

    private LedgerMigrationRow readRow(Row row) {
        String taken = text(row.getCell(COL_TAKEN));
        return new LedgerMigrationRow(
                text(row.getCell(COL_ID)),
                text(row.getCell(COL_ARCHIVE)),
                text(row.getCell(COL_ENTRY)),
                text(row.getCell(COL_FILENAME)),
                text(row.getCell(COL_MIME)),
                longNumber(row.getCell(COL_SIZE)),
                text(row.getCell(COL_SHA)),
                taken.isBlank() ? null : Instant.parse(taken),
                bool(row.getCell(COL_DUPLICATE)),
                text(row.getCell(COL_STATUS)),
                text(row.getCell(COL_DESTINATION_ACCOUNT)),
                text(row.getCell(COL_DESTINATION_REF)),
                text(row.getCell(COL_ERROR)),
                integer(row.getCell(COL_ATTEMPTS))
        );
    }

    private void indexRows() {
        for (int i = 1; i <= items.getLastRowNum(); i++) {
            Row row = items.getRow(i);
            if (row == null) continue;
            String id = text(row.getCell(COL_ID));
            if (!id.isBlank()) rowById.put(id, i);
        }
    }

    private void ensureAttemptsColumn() {
        Row header = items.getRow(0) == null ? items.createRow(0) : items.getRow(0);
        header.createCell(COL_ATTEMPTS).setCellValue("Attempts");
        boolean changed = false;
        for (int i = 1; i <= items.getLastRowNum(); i++) {
            Row row = items.getRow(i);
            if (row != null && row.getCell(COL_ATTEMPTS) == null) {
                row.createCell(COL_ATTEMPTS).setCellValue(0);
                changed = true;
            }
        }
        if (changed) dirtyUpdates++;
    }

    private void appendAudit(String itemId, String status, String details) {
        int rowNum = Math.max(1, audit.getLastRowNum() + 1);
        Row row = audit.createRow(rowNum);
        row.createCell(0).setCellValue(Instant.now().toString());
        row.createCell(1).setCellValue(itemId + " -> " + status);
        row.createCell(2).setCellValue(details);
    }

    private void appendFailure(Row itemRow, String error) {
        int rowNum = Math.max(1, failures.getLastRowNum() + 1);
        Row row = failures.createRow(rowNum);
        row.createCell(0).setCellValue(Instant.now().toString());
        row.createCell(1).setCellValue(text(itemRow.getCell(COL_ID)));
        row.createCell(2).setCellValue(text(itemRow.getCell(COL_ARCHIVE)) + "!" + text(itemRow.getCell(COL_ENTRY)));
        row.createCell(3).setCellValue(error == null ? "" : error);
    }

    private Sheet requiredSheet(String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) throw new IllegalStateException("Ledger is missing " + name + " sheet");
        return sheet;
    }

    private Sheet getOrCreateSheet(String name, String... headers) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) sheet = workbook.createSheet(name);
        Row header = sheet.getRow(0) == null ? sheet.createRow(0) : sheet.getRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        return sheet;
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

    private long longNumber(Cell cell) {
        if (cell == null) return 0L;
        if (cell.getCellType() == CellType.NUMERIC) return (long) cell.getNumericCellValue();
        String value = text(cell);
        return value.isBlank() ? 0L : Long.parseLong(value);
    }

    private int integer(Cell cell) {
        return Math.toIntExact(longNumber(cell));
    }

    private boolean bool(Cell cell) {
        if (cell == null) return false;
        return cell.getCellType() == CellType.BOOLEAN ? cell.getBooleanCellValue() : Boolean.parseBoolean(text(cell));
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Migration ledger is already closed");
    }

    @Override
    public void close() {
        if (closed) return;
        try {
            checkpoint();
            workbook.close();
            closed = true;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to close migration ledger", e);
        }
    }
}
