package com.paritosh.photosmigrator.service;

import com.paritosh.photosmigrator.excel.ExcelLedgerService;
import com.paritosh.photosmigrator.model.MigrationItem;
import com.paritosh.photosmigrator.model.MigrationStatus;
import com.paritosh.photosmigrator.storage.LedgerSnapshot;
import com.paritosh.photosmigrator.storage.LedgerStorage;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class MigrationLedgerService {
    private final LedgerStorage storage;
    private final ExcelLedgerService excel;

    public MigrationLedgerService(LedgerStorage storage, ExcelLedgerService excel) {
        this.storage = storage;
        this.excel = excel;
    }

    public synchronized void initialize(String migrationId) {
        LedgerSnapshot snapshot = storage.load(migrationId);
        if (!snapshot.exists()) {
            storage.save(migrationId, excel.createEmptyWorkbook(migrationId), 0L);
        }
    }

    public synchronized void upsert(String migrationId, MigrationItem item) {
        LedgerSnapshot snapshot = storage.load(migrationId);
        byte[] updated = excel.upsertItem(snapshot.bytes(), migrationId, item);
        storage.save(migrationId, updated, snapshot.generation());
    }

    public List<MigrationItem> items(String migrationId) {
        return excel.readItems(storage.load(migrationId).bytes());
    }

    public Map<MigrationStatus, Long> summary(String migrationId) {
        Map<MigrationStatus, Long> result = new EnumMap<>(MigrationStatus.class);
        for (MigrationItem item : items(migrationId)) {
            result.merge(item.status(), 1L, Long::sum);
        }
        return result;
    }
}
