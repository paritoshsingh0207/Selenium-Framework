package com.paritosh.photosmigrator.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TakeoutScanner {
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"), Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"), Map.entry("bmp", "image/bmp"),
            Map.entry("avif", "image/avif"), Map.entry("ico", "image/x-icon"),
            Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"),
            Map.entry("m4v", "video/x-m4v"), Map.entry("avi", "video/x-msvideo"),
            Map.entry("mkv", "video/x-matroska"), Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("3gp", "video/3gpp"), Map.entry("3g2", "video/3gpp2"),
            Map.entry("mpg", "video/mpeg"), Map.entry("mpeg", "video/mpeg"),
            Map.entry("mts", "video/mp2t"), Map.entry("m2ts", "video/mp2t")
    );

    private final ObjectMapper objectMapper;

    public TakeoutScanner() {
        this(new ObjectMapper());
    }

    TakeoutScanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TakeoutMediaItem> scan(Path takeoutPath) {
        List<Path> archives = discoverArchives(takeoutPath);
        if (archives.isEmpty()) {
            throw new IllegalArgumentException("No .zip Google Takeout archives found under: " + takeoutPath);
        }

        List<TakeoutMediaItem> items = new ArrayList<>();
        Map<String, String> firstByFingerprint = new LinkedHashMap<>();
        for (Path archive : archives) {
            scanArchive(archive, firstByFingerprint, items);
        }
        return List.copyOf(items);
    }

    public List<Path> discoverArchives(Path takeoutPath) {
        if (takeoutPath == null || !Files.exists(takeoutPath)) {
            throw new IllegalArgumentException("Takeout path does not exist: " + takeoutPath);
        }
        try {
            if (Files.isRegularFile(takeoutPath)) {
                return isZip(takeoutPath) ? List.of(takeoutPath.toAbsolutePath().normalize()) : List.of();
            }
            try (var stream = Files.walk(takeoutPath)) {
                return stream.filter(Files::isRegularFile)
                        .filter(this::isZip)
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to discover Takeout archives", e);
        }
    }

    private void scanArchive(Path archive, Map<String, String> firstByFingerprint, List<TakeoutMediaItem> items) {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Map<String, ZipEntry> sidecars = indexSidecars(zip);
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                    continue;
                }
                String mimeType = mimeType(entry.getName());
                if (mimeType == null) {
                    continue;
                }

                DigestResult digest;
                try (InputStream in = zip.getInputStream(entry)) {
                    digest = digest(in);
                }
                long size = entry.getSize() >= 0 ? entry.getSize() : digest.bytesRead();
                String sourceRef = archive.toAbsolutePath().normalize() + "!" + entry.getName();
                String id = UUID.nameUUIDFromBytes(sourceRef.getBytes(StandardCharsets.UTF_8)).toString();
                String fingerprint = digest.sha256() + ":" + size;
                String duplicateOf = firstByFingerprint.putIfAbsent(fingerprint, id);
                Instant takenTime = readTakenTime(zip, sidecars, entry.getName());

                items.add(new TakeoutMediaItem(
                        id,
                        archive.toAbsolutePath().normalize().toString(),
                        entry.getName(),
                        fileName(entry.getName()),
                        mimeType,
                        size,
                        digest.sha256(),
                        takenTime,
                        duplicateOf != null,
                        duplicateOf == null ? "" : duplicateOf
                ));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan Takeout archive: " + archive, e);
        }
    }

    private Map<String, ZipEntry> indexSidecars(ZipFile zip) {
        Map<String, ZipEntry> result = new HashMap<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                result.put(entry.getName().toLowerCase(Locale.ROOT), entry);
            }
        }
        return result;
    }

    private Instant readTakenTime(ZipFile zip, Map<String, ZipEntry> sidecars, String mediaEntryName) {
        ZipEntry sidecar = sidecars.get((mediaEntryName + ".json").toLowerCase(Locale.ROOT));
        if (sidecar == null) {
            String withoutExtension = stripExtension(mediaEntryName);
            sidecar = sidecars.get((withoutExtension + ".json").toLowerCase(Locale.ROOT));
        }
        if (sidecar == null) {
            return null;
        }

        try (InputStream in = zip.getInputStream(sidecar)) {
            JsonNode json = objectMapper.readTree(in);
            String timestamp = json.path("photoTakenTime").path("timestamp").asText("");
            if (timestamp.isBlank()) {
                timestamp = json.path("creationTime").path("timestamp").asText("");
            }
            if (timestamp.isBlank()) {
                return null;
            }
            return Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private DigestResult digest(InputStream input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            long bytes = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                md.update(buffer, 0, read);
                bytes += read;
            }
            return new DigestResult(toHex(md.digest()), bytes);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Unable to hash Takeout media", e);
        }
    }

    private String mimeType(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return MIME_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private boolean isZip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private String fileName(String entryName) {
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash >= 0 ? entryName.substring(slash + 1) : entryName;
    }

    private String stripExtension(String name) {
        int slash = name.lastIndexOf('/');
        int dot = name.lastIndexOf('.');
        return dot > slash ? name.substring(0, dot) : name;
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private record DigestResult(String sha256, long bytesRead) { }
}
