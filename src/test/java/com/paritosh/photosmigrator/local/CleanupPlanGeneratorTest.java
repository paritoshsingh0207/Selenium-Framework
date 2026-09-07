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

class CleanupPlanGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void marksOnlyFullyVerifiedYearsSafeAndBlocksUnknownDates() throws Exception {
        Path ledger = tempDir.resolve("ledger.xlsx");
        TakeoutInventoryLedger inventory = new TakeoutInventoryLedger();
        TakeoutMediaItem oldA = item("a", "2020-01-10T00:00:00Z");
        TakeoutMediaItem oldB = item("b", "2020-12-10T00:00:00Z");
        TakeoutMediaItem pending = item("c", "2021-01-01T00:00:00Z");
        TakeoutMediaItem unknown = new TakeoutMediaItem("d", "a.zip", "d.jpg", "d.jpg",
                "image/jpeg", 10, "dhash", null, false, "");
        inventory.write(ledger, List.of(oldA, oldB, pending, unknown));
        inventory.applyAllocation(ledger, new AllocationResult(
                List.of(
                        new AllocationResult.Assignment("a", "dest@example.com", 10),
                        new AllocationResult.Assignment("b", "dest@example.com", 10),
                        new AllocationResult.Assignment("c", "dest@example.com", 10),
                        new AllocationResult.Assignment("d", "dest@example.com", 10)),
                List.of(),
                java.util.Map.of("dest@example.com", 40L),
                java.util.Map.of("dest@example.com", 100L)));
        try (LocalMigrationLedger state = LocalMigrationLedger.open(ledger, 1)) {
            state.update("a", "VERIFIED", "media-a", "", false, true);
            state.update("b", "VERIFIED", "media-b", "", false, true);
            state.update("d", "VERIFIED", "media-d", "", false, true);
        }

        List<CleanupBatch> batches = new CleanupPlanGenerator().generate(ledger);

        assertThat(batches).extracting(CleanupBatch::batch)
                .containsExactly("2020", "2021", "UNKNOWN_DATE");
        assertThat(batches.get(0).status()).isEqualTo("SAFE_TO_DELETE_MANUALLY");
        assertThat(batches.get(0).verified()).isEqualTo(2);
        assertThat(batches.get(1).status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(batches.get(2).status()).isEqualTo("BLOCKED_UNKNOWN_CAPTURE_DATE");

        try (InputStream in = Files.newInputStream(ledger); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertThat(workbook.getSheet("Cleanup Plan").getRow(1).getCell(6).getStringCellValue())
                    .isEqualTo("SAFE_TO_DELETE_MANUALLY");
        }
    }

    private TakeoutMediaItem item(String id, String time) {
        return new TakeoutMediaItem(id, "a.zip", id + ".jpg", id + ".jpg", "image/jpeg",
                10, id + "hash", Instant.parse(time), false, "");
    }
}
