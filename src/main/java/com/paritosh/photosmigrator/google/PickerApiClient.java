package com.paritosh.photosmigrator.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class PickerApiClient {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public PickerApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode createSession(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.PICKER_BASE + "/sessions"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return sendJson(request);
    }

    public JsonNode getSession(String accessToken, String sessionId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.PICKER_BASE + "/sessions/" + encode(sessionId)))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return sendJson(request);
    }

    public JsonNode listMediaItems(String accessToken, String sessionId, int pageSize, String pageToken) {
        StringBuilder url = new StringBuilder(GooglePhotosEndpoints.PICKER_BASE)
                .append("/mediaItems?sessionId=").append(encode(sessionId))
                .append("&pageSize=").append(pageSize);
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pageToken=").append(encode(pageToken));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return sendJson(request);
    }

    public byte[] downloadMedia(String accessToken, String baseUrl, boolean video) {
        String downloadUrl = baseUrl + (video ? "=dv" : "=d");
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            ensureSuccess(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos media download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos media download interrupted", e);
        }
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos Picker API call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos Picker API call interrupted", e);
        }
    }

    private void ensureSuccess(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Google Photos API returned HTTP " + status + ": " + body);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
