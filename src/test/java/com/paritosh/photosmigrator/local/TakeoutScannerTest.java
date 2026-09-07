package com.paritosh.photosmigrator.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TakeoutScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansTakeoutArchivesReadsSidecarTimeAndFlagsDuplicates() throws Exception {
        byte[] photo = "same-photo-bytes".getBytes(StandardCharsets.UTF_8);
        createZip(tempDir.resolve("takeout-001.zip"), "Takeout/Google Photos/2019/IMG_0001.jpg", photo,
                "{\"photoTakenTime\":{\"timestamp\":\"1577836800\"}}");
        createZip(tempDir.resolve("takeout-002.zip"), "Takeout/Google Photos/Album/IMG_COPY.jpg", photo, null);

        List<TakeoutMediaItem> items = new TakeoutScanner().scan(tempDir);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).mimeType()).isEqualTo("image/jpeg");
        assertThat(items.get(0).takenTime()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(items.get(0).duplicate()).isFalse();
        assertThat(items.get(1).duplicate()).isTrue();
        assertThat(items.get(1).duplicateOf()).isEqualTo(items.get(0).id());
        assertThat(items.get(1).sha256()).isEqualTo(items.get(0).sha256());
    }

    private void createZip(Path zipPath, String mediaPath, byte[] media, String sidecarJson) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry(mediaPath));
            zip.write(media);
            zip.closeEntry();
            if (sidecarJson != null) {
                zip.putNextEntry(new ZipEntry(mediaPath + ".json"));
                zip.write(sidecarJson.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
