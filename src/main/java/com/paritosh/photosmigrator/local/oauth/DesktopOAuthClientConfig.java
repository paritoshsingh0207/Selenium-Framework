package com.paritosh.photosmigrator.local.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DesktopOAuthClientConfig(
        String clientId,
        String clientSecret,
        String authorizationEndpoint,
        String tokenEndpoint
) {
    public static DesktopOAuthClientConfig fromJson(Path credentialsJson) {
        if (credentialsJson == null || !Files.isRegularFile(credentialsJson)) {
            throw new IllegalArgumentException("OAuth credentials JSON does not exist: " + credentialsJson);
        }
        try {
            JsonNode root = new ObjectMapper().readTree(credentialsJson.toFile());
            JsonNode node = root.has("installed") ? root.path("installed") : root.path("web");
            String clientId = required(node, "client_id");
            String clientSecret = node.path("client_secret").asText("");
            String auth = node.path("auth_uri").asText("https://accounts.google.com/o/oauth2/v2/auth");
            String token = node.path("token_uri").asText("https://oauth2.googleapis.com/token");
            return new DesktopOAuthClientConfig(clientId, clientSecret, auth, token);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read OAuth credentials JSON", e);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("OAuth credentials JSON is missing " + field);
        return value;
    }
}
