package com.paritosh.photosmigrator.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class LibraryUploadClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public LibraryUploadClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String uploadBytes(String destinationAccessToken, String mimeType, byte[] bytes) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.LIBRARY_UPLOADS))
                .header("Authorization", "Bearer " + destinationAccessToken)
                .header("Content-Type", "application/octet-stream")
                .header("X-Goog-Upload-Content-Type", mimeType)
                .header("X-Goog-Upload-Protocol", "raw")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos byte upload failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos byte upload interrupted", e);
        }
    }

    public JsonNode createMediaItem(String destinationAccessToken, String uploadToken, String fileName) {
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
            return objectMapper.readTree(response.body());
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
}
