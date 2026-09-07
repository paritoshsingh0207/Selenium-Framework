package com.paritosh.photosmigrator.local;

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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CleanupPlanGenerator {
    public List<CleanupBatch> generate(Path ledgerPath) {
        List<LedgerMigrationRow> rows;
        try (LocalMigrationLedger ledger = LocalMigrationLedger.open(ledgerPath, 1)) {
            rows = ledger.rows();
        }

        Map<Integer, List<LedgerMigrationRow>> byYear = new TreeMap<>();
        List<LedgerMigrationRow> unknownDate = new ArrayList<>();
        for (LedgerMigrationRow row : rows) {
            if (row.duplicate()) continue;
            if (row.takenTime() == null) {
                unknownDate.add(row);
            } else {
                int year = row.takenTime().atZone(ZoneOffset.UTC).getYear();
                byYear.computeIfAbsent(year, ignored -> new ArrayList<>()).add(row);
            }
        }

        List<CleanupBatch> batches = new ArrayList<>();
        for (Map.Entry<Integer, List<LedgerMigrationRow>> entry : byYear.entrySet()) {
            int year = entry.getKey();
            List<LedgerMigrationRow> yearRows = entry.getValue();
            long verified = yearRows.stream().filter(row -> "VERIFIED".equals(row.status())).count();
            String status = verified == yearRows.size() && !yearRows.isEmpty()
                    ? "SAFE_TO_DELETE_MANUALLY"
                    : blockedStatus(yearRows);
            batches.add(new CleanupBatch(
                    Integer.toString(year),
                    year + "-01-01",
                    year + "-12-31",
                    destinations(yearRows),
                    yearRows.size(),
                    verified,
                    status));
        }
        if (!unknownDate.isEmpty()) {
            long verified = unknownDate.stream().filter(row -> "VERIFIED".equals(row.status())).count();
            batches.add(new CleanupBatch(
                    "UNKNOWN_DATE",
                    "",
                    "",
                    destinations(unknownDate),
                    unknownDate.size(),
                    verified,
                    "BLOCKED_UNKNOWN_CAPTURE_DATE"));
        }

        writePlan(ledgerPath, batches);
        return List.copyOf(batches);
    }

    private String blockedStatus(List<LedgerMigrationRow> rows) {
        if (rows.stream().anyMatch(row -> "NEEDS_RECONCILIATION".equals(row.status()))) {
            return "BLOCKED_RECONCILIATION";
        }
        if (rows.stream().anyMatch(row -> "FAILED_FINAL".equals(row.status()))) {
            return "BLOCKED_FAILED_ITEMS";
        }
        return "PENDING_VERIFICATION";
    }

    private String destinations(List<LedgerMigrationRow> rows) {
        LinkedHashSet<String> accounts = new LinkedHashSet<>();
        rows.stream()
                .sorted(Comparator.comparing(LedgerMigrationRow::destinationAccount))
                .map(LedgerMigrationRow::destinationAccount)
                .filter(value -> value != null && !value.isBlank())
                .forEach(accounts::add);
        return String.join(", ", accounts);
    }

    private void writePlan(Path ledgerPath, List<CleanupBatch> batches) {
        Path absolute = ledgerPath.toAbsolutePath().normalize();
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".cleanup.tmp");
        try (InputStream in = Files.newInputStream(absolute); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            Sheet plan = workbook.getSheet("Cleanup Plan");
            if (plan == null) plan = workbook.createSheet("Cleanup Plan");
            clear(plan);
            String[] headers = {"Batch", "From", "To", "Destination", "Expected", "Verified", "Status"};
            Row header = plan.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int rowNum = 1;
            for (CleanupBatch batch : batches) {
                Row row = plan.createRow(rowNum++);
                row.createCell(0).setCellValue(batch.batch());
                row.createCell(1).setCellValue(batch.from());
                row.createCell(2).setCellValue(batch.to());
                row.createCell(3).setCellValue(batch.destinations());
                row.createCell(4).setCellValue(batch.expected());
                row.createCell(5).setCellValue(batch.verified());
                row.createCell(6).setCellValue(batch.status());
            }
            plan.createFreezePane(0, 1);
            if (rowNum > 1) {
                plan.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));
            }

            Sheet audit = workbook.getSheet("Audit");
            if (audit == null) audit = workbook.createSheet("Audit");
            Row auditRow = audit.createRow(Math.max(1, audit.getLastRowNum() + 1));
            auditRow.createCell(0).setCellValue(Instant.now().toString());
            auditRow.createCell(1).setCellValue("CLEANUP_PLAN_GENERATED");
            long safe = batches.stream().filter(batch -> "SAFE_TO_DELETE_MANUALLY".equals(batch.status())).count();
            auditRow.createCell(2).setCellValue("Batches=" + batches.size() + ", Safe=" + safe);

            try (OutputStream out = Files.newOutputStream(temp)) {
                workbook.write(out);
            }
            replaceAtomically(temp, absolute);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to generate cleanup plan: " + absolute, e);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    private void clear(Sheet sheet) {
        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null) sheet.removeRow(row);
        }
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
