package com.paritosh.photosmigrator.excel;

import com.paritosh.photosmigrator.model.MigrationItem;
import com.paritosh.photosmigrator.model.MigrationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelLedgerServiceTest {
    @Test
    void createsAndReadsAnItem() {
        ExcelLedgerService service = new ExcelLedgerService();
        byte[] empty = service.createEmptyWorkbook("test-migration");
        MigrationItem item = new MigrationItem("1", "photo.jpg", "image/jpeg", 123L,
                "abc", MigrationStatus.VERIFIED, 1, "source", "destination", Instant.now(), "");

        byte[] updated = service.upsertItem(empty, "test-migration", item);

        assertThat(service.readItems(updated)).hasSize(1);
        assertThat(service.readItems(updated).getFirst().fileName()).isEqualTo("photo.jpg");
        assertThat(service.readItems(updated).getFirst().status()).isEqualTo(MigrationStatus.VERIFIED);
    }
}
