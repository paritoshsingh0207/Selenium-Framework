package com.paritosh.photosmigrator.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paritosh.photosmigrator.google.GooglePhotosEndpoints;
import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class LocalLibraryUploadClient {
    private static final int DEFAULT_GRANULARITY = 256 * 1024;
    private static final int DEFAULT_CHUNK_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RETRIES = 5;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String uploadsEndpoint;
    private final String batchCreateEndpoint;
    private final String mediaItemsEndpoint;
    private final int preferredChunkBytes;

    public LocalLibraryUploadClient() {
        this(new ObjectMapper(), HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                GooglePhotosEndpoints.LIBRARY_UPLOADS,
                GooglePhotosEndpoints.LIBRARY_BATCH_CREATE,
                GooglePhotosEndpoints.LIBRARY_MEDIA_ITEMS,
                DEFAULT_CHUNK_BYTES);
    }

    LocalLibraryUploadClient(ObjectMapper objectMapper,
                             HttpClient httpClient,
                             String uploadsEndpoint,
                             String batchCreateEndpoint,
                             String mediaItemsEndpoint,
                             int preferredChunkBytes) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.uploadsEndpoint = uploadsEndpoint;
        this.batchCreateEndpoint = batchCreateEndpoint;
        this.mediaItemsEndpoint = mediaItemsEndpoint;
        this.preferredChunkBytes = preferredChunkBytes;
    }

    public CreatedMediaItem uploadAndCreate(AccessTokenProvider tokens, TakeoutMediaItem item) {
        if (item.duplicate()) throw new IllegalArgumentException("Duplicate Takeout entries must not be uploaded: " + item.sourceRef());
        if (item.mimeType() == null || item.mimeType().isBlank()) {
            throw new IllegalArgumentException("Media MIME type is required: " + item.sourceRef());
        }
        TakeoutEntrySource source = TakeoutEntrySource.from(item);
        String uploadToken = uploadResumable(tokens, source, item.mimeType());
        return createMediaItem(tokens, uploadToken, item.fileName());
    }

    public DestinationMediaItem verify(AccessTokenProvider tokens, String mediaItemId) {
        HttpRequest request = requestWithAuth(URI.create(mediaItemsEndpoint + "/" + encode(mediaItemId)), tokens.accessToken())
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<String> response = sendWithAuthRetry(request, tokens,
                token -> requestWithAuth(URI.create(mediaItemsEndpoint + "/" + encode(mediaItemId)), token)
                        .timeout(Duration.ofMinutes(2)).GET().build(),
                "Google Photos destination verification failed");
        JsonNode mediaItem = parseJson(response.body(), "Google Photos destination verification returned invalid JSON");
        return new DestinationMediaItem(
                mediaItem.path("id").asText(mediaItemId),
                mediaItem.path("mimeType").asText(""),
                mediaItem.path("mediaMetadata").path("video").path("status").asText(""));
    }

    private String uploadResumable(AccessTokenProvider tokens, TakeoutEntrySource source, String mimeType) {
        UploadSession session = startSession(tokens, mimeType, source.sizeBytes());
        long offset = 0;
        int retryCount = 0;
        InputStream stream = source.open(0);
        try {
            while (offset < source.sizeBytes()) {
                int requested = chunkLength(source.sizeBytes() - offset, session.granularity());
                byte[] chunk = stream.readNBytes(requested);
                if (chunk.length != requested) {
                    throw new IllegalStateException("Takeout media ended early at byte " + offset);
                }
                boolean last = offset + chunk.length == source.sizeBytes();
                try {
                    HttpResponse<String> response = sendChunk(tokens, session.url(), offset, chunk, last);
                    retryCount = 0;
                    offset += chunk.length;
                    if (last) {
                        String uploadToken = response.body().trim();
                        if (uploadToken.isBlank()) throw new IllegalStateException("Google Photos returned an empty upload token");
                        return uploadToken;
                    }
                } catch (RetryableUploadException retryable) {
                    if (++retryCount > MAX_RETRIES) throw retryable;
                    long serverOffset = queryOffset(tokens, session.url());
                    if (serverOffset < 0 || serverOffset > source.sizeBytes()) {
                        throw new IllegalStateException("Google Photos returned invalid resumable offset: " + serverOffset);
                    }
                    stream.close();
                    offset = serverOffset;
                    stream = source.open(offset);
                    sleepBackoff(retryCount);
                }
            }
            throw new IllegalStateException("Resumable upload ended without an upload token");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Takeout media during upload", e);
        } finally {
            try { stream.close(); } catch (IOException ignored) { }
        }
    }

    private UploadSession startSession(AccessTokenProvider tokens, String mimeType, long sizeBytes) {
        HttpRequest request = startRequest(tokens.accessToken(), mimeType, sizeBytes);
        HttpResponse<String> response = sendWithAuthRetry(request, tokens,
                token -> startRequest(token, mimeType, sizeBytes),
                "Unable to start Google Photos resumable upload");
        String url = response.headers().firstValue("X-Goog-Upload-URL")
                .orElseThrow(() -> new IllegalStateException("Google Photos did not return X-Goog-Upload-URL"));
        int granularity = response.headers().firstValue("X-Goog-Upload-Chunk-Granularity")
                .map(value -> parsePositiveInt(value, DEFAULT_GRANULARITY))
                .orElse(DEFAULT_GRANULARITY);
        return new UploadSession(url, granularity);
    }

    private HttpRequest startRequest(String accessToken, String mimeType, long sizeBytes) {
        return requestWithAuth(URI.create(uploadsEndpoint), accessToken)
                .timeout(Duration.ofMinutes(2))
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Raw-Size", Long.toString(sizeBytes))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private HttpResponse<String> sendChunk(AccessTokenProvider tokens, String url, long offset, byte[] chunk, boolean last) {
        String command = last ? "upload, finalize" : "upload";
        HttpRequest request = chunkRequest(url, tokens.accessToken(), offset, chunk, command);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                response = httpClient.send(chunkRequest(url, tokens.refreshAccessToken(), offset, chunk, command),
                        HttpResponse.BodyHandlers.ofString());
            }
            if (isRetryable(response.statusCode())) {
                throw new RetryableUploadException("Google Photos upload returned HTTP " + response.statusCode());
            }
            ensureSuccess(response.statusCode(), response.body(), "Google Photos chunk upload failed");
            return response;
        } catch (IOException e) {
            throw new RetryableUploadException("Google Photos chunk upload connection failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos chunk upload interrupted", e);
        }
    }

    private HttpRequest chunkRequest(String url, String accessToken, long offset, byte[] chunk, String command) {
        return requestWithAuth(URI.create(url), accessToken)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/octet-stream")
                .header("X-Goog-Upload-Command", command)
                .header("X-Goog-Upload-Offset", Long.toString(offset))
                .POST(HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();
    }

    private long queryOffset(AccessTokenProvider tokens, String url) {
        HttpRequest request = queryRequest(url, tokens.accessToken());
        HttpResponse<String> response = sendWithAuthRetry(request, tokens,
                token -> queryRequest(url, token), "Unable to query Google Photos resumable upload");
        String status = response.headers().firstValue("X-Goog-Upload-Status").orElse("active");
        if (!"active".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Google Photos resumable session is no longer active: " + status);
        }
        return response.headers().firstValue("X-Goog-Upload-Size-Received")
                .map(Long::parseLong)
                .orElse(0L);
    }

    private HttpRequest queryRequest(String url, String token) {
        return requestWithAuth(URI.create(url), token)
                .timeout(Duration.ofMinutes(2))
                .header("X-Goog-Upload-Command", "query")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String uploadToken, String fileName) {
        ObjectNode simple = objectMapper.createObjectNode();
        simple.put("fileName", fileName);
        simple.put("uploadToken", uploadToken);
        ObjectNode item = objectMapper.createObjectNode();
        item.set("simpleMediaItem", simple);
        ArrayNode items = objectMapper.createArrayNode().add(item);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("newMediaItems", items);

        HttpRequest request = createRequest(tokens.accessToken(), body.toString());
        HttpResponse<String> response = sendWithAuthRetry(request, tokens,
                token -> createRequest(token, body.toString()), "Google Photos media creation failed");
        JsonNode json = parseJson(response.body(), "Google Photos media creation returned invalid JSON");
        JsonNode result = json.path("newMediaItemResults").path(0);
        int status = result.path("status").path("code").asInt(0);
        if (status != 0) throw new IllegalStateException("Google Photos media creation failed: " + result.path("status"));
        JsonNode media = result.path("mediaItem");
        String id = media.path("id").asText("");
        if (id.isBlank()) throw new IllegalStateException("Google Photos did not return a created media item ID");
        return new CreatedMediaItem(id, media.path("productUrl").asText(""),
                media.path("mediaMetadata").path("video").path("status").asText(""));
    }

    private HttpRequest createRequest(String token, String body) {
        return requestWithAuth(URI.create(batchCreateEndpoint), token)
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpResponse<String> sendWithAuthRetry(HttpRequest request,
                                                   AccessTokenProvider tokens,
                                                   RequestFactory retryFactory,
                                                   String message) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                response = httpClient.send(retryFactory.create(tokens.refreshAccessToken()), HttpResponse.BodyHandlers.ofString());
            }
            ensureSuccess(response.statusCode(), response.body(), message);
            return response;
        } catch (IOException e) {
            throw new IllegalStateException(message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message + " (interrupted)", e);
        }
    }

    int chunkLength(long remaining, int granularity) {
        if (remaining <= 0) return 0;
        int safeGranularity = Math.max(1, granularity);
        if (remaining <= preferredChunkBytes) return Math.toIntExact(remaining);
        int target = Math.max(preferredChunkBytes, safeGranularity);
        int aligned = target - (target % safeGranularity);
        if (aligned <= 0) aligned = safeGranularity;
        return (int) Math.min(remaining, aligned);
    }

    private boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private JsonNode parseJson(String body, String message) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException(message, e);
        }
    }

    private HttpRequest.Builder requestWithAuth(URI uri, String accessToken) {
        return HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + accessToken);
    }

    private void ensureSuccess(int status, String body, String message) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(message + " (HTTP " + status + "): " + body);
        }
    }

    private void sleepBackoff(int retry) {
        try {
            Thread.sleep(Math.min(5_000L, 250L * (1L << Math.min(retry, 4))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry backoff interrupted", e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record UploadSession(String url, int granularity) { }

    @FunctionalInterface
    private interface RequestFactory {
        HttpRequest create(String accessToken);
    }

    private static class RetryableUploadException extends RuntimeException {
        RetryableUploadException(String message) { super(message); }
        RetryableUploadException(String message, Throwable cause) { super(message, cause); }
    }

    public record CreatedMediaItem(String id, String productUrl, String videoStatus) { }
    public record DestinationMediaItem(String id, String mimeType, String videoStatus) { }
}
