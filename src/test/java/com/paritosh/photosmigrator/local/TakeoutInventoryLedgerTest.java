package com.paritosh.photosmigrator.local;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TakeoutInventoryLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesExpectedWorkbookSheetsAndStatuses() throws Exception {
        TakeoutMediaItem first = new TakeoutMediaItem(
                "one", "a.zip", "Photos/one.jpg", "one.jpg", "image/jpeg", 10,
                "abc", Instant.parse("2020-01-01T00:00:00Z"), false, "");
        TakeoutMediaItem duplicate = new TakeoutMediaItem(
                "two", "b.zip", "Photos/two.jpg", "two.jpg", "image/jpeg", 10,
                "abc", null, true, "one");
        Path ledger = tempDir.resolve("ledger.xlsx");

        new TakeoutInventoryLedger().write(ledger, List.of(first, duplicate));

        assertThat(ledger).exists();
        try (InputStream in = Files.newInputStream(ledger); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Items")).isNotNull();
            assertThat(workbook.getSheet("Duplicates")).isNotNull();
            assertThat(workbook.getSheet("Destinations")).isNotNull();
            assertThat(workbook.getSheet("Cleanup Plan")).isNotNull();
            assertThat(workbook.getSheet("Items").getRow(1).getCell(10).getStringCellValue()).isEqualTo("HASHED");
            assertThat(workbook.getSheet("Items").getRow(2).getCell(10).getStringCellValue()).isEqualTo("SKIPPED_DUPLICATE");
        }
    }
}
