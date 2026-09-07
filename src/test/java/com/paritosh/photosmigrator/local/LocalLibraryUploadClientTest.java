package com.paritosh.photosmigrator.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLibraryUploadClientTest {
    @TempDir
    Path tempDir;

    @Test
    void uploadsTakeoutMediaInAlignedChunksThenCreatesAndVerifiesItem() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<Long> offsets = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        int port = server.getAddress().getPort();
        String base = "http://127.0.0.1:" + port;

        server.createContext("/uploads", exchange -> {
            exchange.getResponseHeaders().add("X-Goog-Upload-URL", base + "/session");
            exchange.getResponseHeaders().add("X-Goog-Upload-Chunk-Granularity", "4");
            respond(exchange, 200, "");
        });
        server.createContext("/session", exchange -> {
            String command = exchange.getRequestHeaders().getFirst("X-Goog-Upload-Command");
            if ("query".equals(command)) {
                exchange.getResponseHeaders().add("X-Goog-Upload-Status", "active");
                exchange.getResponseHeaders().add("X-Goog-Upload-Size-Received",
                        Long.toString(lengths.stream().mapToLong(Integer::longValue).sum()));
                respond(exchange, 200, "");
                return;
            }
            offsets.add(Long.parseLong(exchange.getRequestHeaders().getFirst("X-Goog-Upload-Offset")));
            byte[] body = exchange.getRequestBody().readAllBytes();
            lengths.add(body.length);
            respond(exchange, 200, command.contains("finalize") ? "upload-token-1" : "");
        });
        server.createContext("/batchCreate", exchange -> respond(exchange, 200,
                "{\"newMediaItemResults\":[{\"status\":{\"code\":0},\"mediaItem\":{\"id\":\"media-1\",\"productUrl\":\"https://photos.example/media-1\",\"mimeType\":\"image/jpeg\",\"mediaMetadata\":{}}}]}"));
        server.createContext("/mediaItems/media-1", exchange -> respond(exchange, 200,
                "{\"id\":\"media-1\",\"mimeType\":\"image/jpeg\",\"mediaMetadata\":{}}"));
        server.start();

        try {
            Path archive = tempDir.resolve("takeout.zip");
            byte[] bytes = new byte[20];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                zip.putNextEntry(new ZipEntry("Photos/photo.jpg"));
                zip.write(bytes);
                zip.closeEntry();
            }
            TakeoutMediaItem item = new TakeoutMediaItem("id", archive.toString(), "Photos/photo.jpg",
                    "photo.jpg", "image/jpeg", bytes.length, "sha", null, false, "");

            LocalLibraryUploadClient client = new LocalLibraryUploadClient(new ObjectMapper(), HttpClient.newHttpClient(),
                    base + "/uploads", base + "/batchCreate", base + "/mediaItems", 8);
            AccessTokenProvider token = new StaticTokenProvider();

            LocalLibraryUploadClient.CreatedMediaItem created = client.uploadAndCreate(token, item);
            LocalLibraryUploadClient.DestinationMediaItem verified = client.verify(token, created.id());

            assertThat(offsets).containsExactly(0L, 8L, 16L);
            assertThat(lengths).containsExactly(8, 8, 4);
            assertThat(created.id()).isEqualTo("media-1");
            assertThat(verified.id()).isEqualTo("media-1");
            assertThat(verified.mimeType()).isEqualTo("image/jpeg");
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static class StaticTokenProvider implements AccessTokenProvider {
        @Override public String accessToken() { return "token"; }
        @Override public String refreshAccessToken() { return "token-refreshed"; }
    }
}
