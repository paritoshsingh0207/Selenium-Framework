package com.paritosh.photosmigrator.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@ConditionalOnMissingBean(LedgerStorage.class)
public class LocalLedgerStorage implements LedgerStorage {
    private final Path directory = Path.of(System.getProperty("java.io.tmpdir"), "photos-migrator-ledgers");

    @Override
    public synchronized LedgerSnapshot load(String migrationId) {
        try {
            Files.createDirectories(directory);
            Path ledger = pathFor(migrationId);
            if (!Files.exists(ledger)) {
                return new LedgerSnapshot(new byte[0], 0L);
            }
            return new LedgerSnapshot(Files.readAllBytes(ledger), Files.getLastModifiedTime(ledger).toMillis());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read local migration ledger", e);
        }
    }

    @Override
    public synchronized long save(String migrationId, byte[] workbookBytes, long expectedGeneration) {
        try {
            Files.createDirectories(directory);
            Path ledger = pathFor(migrationId);
            if (Files.exists(ledger) && expectedGeneration > 0L) {
                long current = Files.getLastModifiedTime(ledger).toMillis();
                if (current != expectedGeneration) {
                    throw new IllegalStateException("Ledger changed since it was read; reload before retrying");
                }
            }
            Path temp = Files.createTempFile(directory, "ledger-", ".xlsx");
            Files.write(temp, workbookBytes);
            Files.move(temp, ledger, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Files.getLastModifiedTime(ledger).toMillis();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write local migration ledger", e);
        }
    }

    private Path pathFor(String migrationId) {
        String safeId = migrationId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return directory.resolve(safeId + ".xlsx");
    }
}
