package com.paritosh.photosmigrator.storage;

public interface LedgerStorage {
    LedgerSnapshot load(String migrationId);
    long save(String migrationId, byte[] workbookBytes, long expectedGeneration);
}
