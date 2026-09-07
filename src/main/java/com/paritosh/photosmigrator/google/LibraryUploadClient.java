package com.paritosh.photosmigrator.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class LibraryUploadClient {
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper objectMapper;

    public LibraryUploadClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String uploadStream(String destinationAccessToken, String mimeType, InputStream inputStream, long contentLength) {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);
        if (contentLength >= 0) {
            publisher = HttpRequest.BodyPublishers.fromPublisher(publisher, contentLength);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.LIBRARY_UPLOADS))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .header("Content-Type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "raw")
                .POST(publisher)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            String uploadToken = response.body().trim();
            if (uploadToken.isBlank()) throw new IllegalStateException("Google Photos returned an empty upload token");
            return uploadToken;
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos byte upload failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos byte upload interrupted", e);
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
                throw new IllegalStateException("Google Photos media creation failed: " + result.path("status").toString());
            }
            String mediaItemId = result.path("mediaItem").path("id").asText("");
            if (mediaItemId.isBlank()) {
                throw new IllegalStateException("Google Photos did not return the created media item ID");
            }
            return new CreatedMediaItem(mediaItemId, result.path("mediaItem").path("productUrl").asText(""));
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos media creation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos media creation interrupted", e);
        }
    }

    private void ensureSuccess(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Google Photos API returned HTTP " + status + ": " + body);
        }
    }

    public record CreatedMediaItem(String id, String productUrl) { }
}
