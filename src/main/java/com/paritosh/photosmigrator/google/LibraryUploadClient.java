package com.paritosh.photosmigrator.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class LibraryUploadClient {
    private static final long RESUMABLE_THRESHOLD_BYTES = 50L * 1024L * 1024L;

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper objectMapper;

    public LibraryUploadClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String uploadStream(String destinationAccessToken, String mimeType, InputStream inputStream, long contentLength) {
        boolean video = mimeType != null && mimeType.startsWith("video/");
        if (contentLength >= 0 && (video || contentLength >= RESUMABLE_THRESHOLD_BYTES)) {
            return uploadResumable(destinationAccessToken, mimeType, inputStream, contentLength);
        }
        return uploadRaw(destinationAccessToken, mimeType, inputStream, contentLength);
    }

    private String uploadRaw(String destinationAccessToken, String mimeType, InputStream inputStream, long contentLength) {
        HttpRequest.BodyPublisher publisher = bodyPublisher(inputStream, contentLength);
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.LIBRARY_UPLOADS))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .header("Content-Type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "raw")
                .POST(publisher)
                .build();
        return sendForUploadToken(request, "Google Photos raw byte upload failed");
    }

    private String uploadResumable(String destinationAccessToken, String mimeType, InputStream inputStream, long contentLength) {
        HttpRequest start = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.LIBRARY_UPLOADS))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Raw-Size", Long.toString(contentLength))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> startResponse = httpClient.send(start, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(startResponse.statusCode(), startResponse.body());
            String uploadUrl = startResponse.headers().firstValue("X-Goog-Upload-URL")
                    .orElseThrow(() -> new IllegalStateException("Google Photos did not return a resumable upload URL"));

            HttpRequest upload = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + destinationAccessToken)
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .header("X-Goog-Upload-Offset", "0")
                    .POST(bodyPublisher(inputStream, contentLength))
                    .build();
            return sendForUploadToken(upload, "Google Photos resumable byte upload failed");
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos resumable upload start failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos resumable upload start interrupted", e);
        }
    }

    private HttpRequest.BodyPublisher bodyPublisher(InputStream inputStream, long contentLength) {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);
        return contentLength >= 0 ? HttpRequest.BodyPublishers.fromPublisher(publisher, contentLength) : publisher;
    }

    private String sendForUploadToken(HttpRequest request, String message) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            String uploadToken = response.body().trim();
            if (uploadToken.isBlank()) throw new IllegalStateException("Google Photos returned an empty upload token");
            return uploadToken;
        } catch (IOException e) {
            throw new IllegalStateException(message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message + " (interrupted)", e);
        }
    }

    public CreatedMediaItem createMediaItem(String destinationAccessToken, String uploadToken, String fileName) {
        ObjectNode simpleMediaItem = objectMapper.createObjectNode();
        simpleMediaItem.put("fileName", fileName);
        simpleMediaItem.put("uploadToken", uploadToken);
        ObjectNode newMediaItem = objectMapper.createObjectNode();
        newMediaItem.set("simpleMediaItem", simpleMediaItem);
        ArrayNode newMediaItems = objectMapper.createArrayNode().add(newMediaItem);
        ObjectNode body = objectMapper.createObjectNode();
        body.set("newMediaItems", newMediaItems);

        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.LIBRARY_BATCH_CREATE))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode result = json.path("newMediaItemResults").path(0);
            int statusCode = result.path("status").path("code").asInt(0);
            if (statusCode != 0) {
                throw new IllegalStateException("Google Photos media creation failed: " + result.path("status"));
            }
            JsonNode mediaItem = result.path("mediaItem");
            String mediaItemId = mediaItem.path("id").asText("");
            if (mediaItemId.isBlank()) {
                throw new IllegalStateException("Google Photos did not return the created media item ID");
            }
            return new CreatedMediaItem(
                    mediaItemId,
                    mediaItem.path("productUrl").asText(""),
                    videoStatus(mediaItem));
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos media creation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos media creation interrupted", e);
        }
    }

    public DestinationMediaItem getMediaItem(String destinationAccessToken, String mediaItemId) {
        String url = GooglePhotosEndpoints.LIBRARY_MEDIA_ITEMS + "/" + encode(mediaItemId);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            JsonNode mediaItem = objectMapper.readTree(response.body());
            return new DestinationMediaItem(
                    mediaItem.path("id").asText(mediaItemId),
                    mediaItem.path("mimeType").asText(""),
                    videoStatus(mediaItem));
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos destination verification failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos destination verification interrupted", e);
        }
    }

    private String videoStatus(JsonNode mediaItem) {
        return mediaItem.path("mediaMetadata").path("video").path("status").asText("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void ensureSuccess(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Google Photos API returned HTTP " + status + ": " + body);
        }
    }

    public record CreatedMediaItem(String id, String productUrl, String videoStatus) { }

    public record DestinationMediaItem(String id, String mimeType, String videoStatus) { }
}
