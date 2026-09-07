package com.paritosh.photosmigrator.local;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TakeoutEntrySource {
    private final Path archive;
    private final String entryName;
    private final long sizeBytes;

    public TakeoutEntrySource(Path archive, String entryName, long sizeBytes) {
        this.archive = archive.toAbsolutePath().normalize();
        this.entryName = entryName;
        this.sizeBytes = sizeBytes;
    }

    public static TakeoutEntrySource from(TakeoutMediaItem item) {
        return new TakeoutEntrySource(Path.of(item.archivePath()), item.entryName(), item.sizeBytes());
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public InputStream open(long offset) {
        if (offset < 0 || offset > sizeBytes) throw new IllegalArgumentException("Invalid source offset: " + offset);
        try {
            ZipFile zip = new ZipFile(archive.toFile());
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                zip.close();
                throw new IllegalStateException("Takeout ZIP entry no longer exists: " + archive + "!" + entryName);
            }
            InputStream raw = zip.getInputStream(entry);
            try {
                raw.skipNBytes(offset);
            } catch (IOException e) {
                raw.close();
                zip.close();
                throw e;
            }
            return new FilterInputStream(raw) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        zip.close();
                    }
                }
            };
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open Takeout ZIP media stream: " + archive + "!" + entryName, e);
        }
    }
}
