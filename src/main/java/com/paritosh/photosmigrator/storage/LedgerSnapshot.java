package com.paritosh.photosmigrator.storage;

public record LedgerSnapshot(byte[] bytes, long generation) {
    public boolean exists() {
        return bytes != null && bytes.length > 0;
    }
}
