package com.paritosh.photosmigrator.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class PickerApiClient {
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper objectMapper;

    public PickerApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode createSession(String accessToken, int maxItemCount) {
        int safeMax = Math.max(1, Math.min(2000, maxItemCount));
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("pickingConfig").put("maxItemCount", Integer.toString(safeMax));
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.PICKER_BASE + "/sessions"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return sendJson(request);
    }

    public JsonNode getSession(String accessToken, String sessionId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.PICKER_BASE + "/sessions/" + encode(sessionId)))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return sendJson(request);
    }

    public void deleteSession(String accessToken, String sessionId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GooglePhotosEndpoints.PICKER_BASE + "/sessions/" + encode(sessionId)))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Google Photos Picker session deletion failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Photos Picker session deletion interrupted", e);
        }
    }

    public JsonNode listMediaItems(String accessToken, String sessionId, int pageSize, String pageToken) {
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        StringBuilder url = new StringBuilder(GooglePhotosEndpoints.PICKER_BASE)
                .append("/mediaItems?sessionId=").append(encode(sessionId))
                .append("&pageSize=").append(safePageSize);
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pageToken=").append(encode(pageToken));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        return sendJson(request);
    }

    public MediaDownload openMedia(String accessToken, String baseUrl, boolean video) {
        String downloadUrl = baseUrl + (video ? "=dv" : "=d");
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                response.body().close();
                ensureSuccess(response.statusCode(), body);
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return new MediaDownload(response.body(), contentLength);
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

    public record MediaDownload(InputStream inputStream, long contentLength) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
