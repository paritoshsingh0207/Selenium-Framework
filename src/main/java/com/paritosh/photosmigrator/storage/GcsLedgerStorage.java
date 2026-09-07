package com.paritosh.photosmigrator.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ledger.bucket-name")
public class GcsLedgerStorage implements LedgerStorage {
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final Storage storage;
    private final String bucketName;
    private final String objectPrefix;

    public GcsLedgerStorage(
            @Value("${app.ledger.bucket-name}") String bucketName,
            @Value("${app.ledger.object-prefix:migrations}") String objectPrefix) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
    }

    @Override
    public LedgerSnapshot load(String migrationId) {
        Blob blob = storage.get(blobId(migrationId));
        if (blob == null) {
            return new LedgerSnapshot(new byte[0], 0L);
        }
        return new LedgerSnapshot(blob.getContent(), blob.getGeneration());
    }

    @Override
    public long save(String migrationId, byte[] workbookBytes, long expectedGeneration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId(migrationId))
                .setContentType(XLSX_CONTENT_TYPE)
                .build();

        Blob written;
        if (expectedGeneration == 0L) {
            written = storage.create(blobInfo, workbookBytes, Storage.BlobTargetOption.doesNotExist());
        } else {
            written = storage.create(blobInfo, workbookBytes,
                    Storage.BlobTargetOption.generationMatch(expectedGeneration));
        }
        return written.getGeneration();
    }

    private BlobId blobId(String migrationId) {
        String safeId = migrationId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return BlobId.of(bucketName, objectPrefix + "/" + safeId + ".xlsx");
    }
}
