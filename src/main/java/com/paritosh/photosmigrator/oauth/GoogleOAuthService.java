package com.paritosh.photosmigrator.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paritosh.photosmigrator.google.GooglePhotosEndpoints;
import com.paritosh.photosmigrator.model.AccountRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthService {
    private static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper objectMapper;
    private final CredentialVault vault;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

    public GoogleOAuthService(
            ObjectMapper objectMapper,
            CredentialVault vault,
            @Value("${app.google.oauth-client-id:}") String clientId,
            @Value("${app.google.oauth-client-secret:}") String clientSecret,
            @Value("${app.google.oauth-redirect-uri}") String redirectUri) {
        this.objectMapper = objectMapper;
        this.vault = vault;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public String authorizationUrl(AccountRole role) {
        ensureConfigured();
        cleanupPending();
        String state = randomState();
        pending.put(state, new PendingAuthorization(role, Instant.now()));
        String scope = "openid email " + scopeFor(role);
        return AUTHORIZE + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=" + enc("consent select_account")
                + "&scope=" + enc(scope)
                + "&state=" + enc(state);
    }

    public AccountRole completeAuthorization(String state, String code) {
        PendingAuthorization authorization = pending.remove(state);
        if (authorization == null || authorization.createdAt().isBefore(Instant.now().minus(10, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("OAuth state is invalid or expired");
        }
        JsonNode token = tokenRequest("code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code");
        String accessToken = required(token, "access_token");
        String refreshToken = token.path("refresh_token").asText("");
        if (refreshToken.isBlank()) {
            refreshToken = vault.load(authorization.role()).map(OAuthCredential::refreshToken).orElse("");
        }
        long expiresIn = token.path("expires_in").asLong(3600);
        String email = fetchEmail(accessToken);
        ensureDifferentAccount(authorization.role(), email);
        OAuthCredential credential = new OAuthCredential(
                accessToken,
                refreshToken,
                Instant.now().plusSeconds(Math.max(60, expiresIn)),
                email,
                token.path("scope").asText(scopeFor(authorization.role())));
        vault.save(authorization.role(), credential);
        return authorization.role();
    }

    public String accessToken(AccountRole role) {
        OAuthCredential credential = vault.load(role)
                .orElseThrow(() -> new IllegalStateException(role + " Google account is not connected"));
        if (credential.isUsable()) return credential.accessToken();
        if (credential.refreshToken() == null || credential.refreshToken().isBlank()) {
            throw new IllegalStateException(role + " Google authorization needs to be renewed");
        }
        JsonNode token = tokenRequest("client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(credential.refreshToken())
                + "&grant_type=refresh_token");
        OAuthCredential refreshed = new OAuthCredential(
                required(token, "access_token"),
                credential.refreshToken(),
                Instant.now().plusSeconds(token.path("expires_in").asLong(3600)),
                credential.email(),
                token.path("scope").asText(credential.scope()));
        vault.save(role, refreshed);
        return refreshed.accessToken();
    }

    public Optional<String> connectedEmail(AccountRole role) {
        return vault.load(role).map(OAuthCredential::email).filter(value -> !value.isBlank());
    }

    public boolean isConnected(AccountRole role) {
        return vault.load(role).isPresent();
    }

    public void disconnect(AccountRole role) {
        vault.clear(role);
    }

    private void ensureDifferentAccount(AccountRole role, String email) {
        AccountRole other = role == AccountRole.SOURCE ? AccountRole.DESTINATION : AccountRole.SOURCE;
        vault.load(other).map(OAuthCredential::email).filter(email::equalsIgnoreCase).ifPresent(value -> {
            throw new IllegalArgumentException("Source and destination Google Photos accounts must be different");
        });
    }

    private JsonNode tokenRequest(String body) {
        ensureConfigured();
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request, "Google OAuth token request failed");
    }

    private String fetchEmail(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        JsonNode response = sendJson(request, "Unable to identify the connected Google account");
        return required(response, "email");
    }

    private JsonNode sendJson(HttpRequest request, String message) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(message + " (HTTP " + response.statusCode() + "): " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException(message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message + " (interrupted)", e);
        }
    }

    private String scopeFor(AccountRole role) {
        return role == AccountRole.SOURCE ? GooglePhotosEndpoints.PICKER_SCOPE : GooglePhotosEndpoints.LIBRARY_APPEND_SCOPE;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("Google response did not contain " + field);
        return value;
    }

    private void ensureConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth client ID and secret are not configured");
        }
    }

    private String randomState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupPending() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record PendingAuthorization(AccountRole role, Instant createdAt) { }
}
