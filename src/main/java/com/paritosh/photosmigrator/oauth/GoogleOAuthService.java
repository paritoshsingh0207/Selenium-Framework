package com.paritosh.photosmigrator.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paritosh.photosmigrator.google.GooglePhotosEndpoints;
import com.paritosh.photosmigrator.model.AccountRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class GoogleOAuthService {
    private static final String AUTHORIZE = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final long STATE_MAX_AGE_SECONDS = 600;

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper objectMapper;
    private final CredentialVault vault;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String stateSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public GoogleOAuthService(
            ObjectMapper objectMapper,
            CredentialVault vault,
            @Value("${app.google.oauth-client-id:}") String clientId,
            @Value("${app.google.oauth-client-secret:}") String clientSecret,
            @Value("${app.google.oauth-redirect-uri}") String redirectUri,
            @Value("${app.google.oauth-state-secret:}") String stateSecret) {
        this.objectMapper = objectMapper;
        this.vault = vault;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.stateSecret = stateSecret;
    }

    public String authorizationUrl(AccountRole role) {
        ensureConfigured();
        String state = createState(role);
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
        AccountRole role = verifyState(state);
        JsonNode token = tokenRequest("code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code");
        String accessToken = required(token, "access_token");
        String refreshToken = token.path("refresh_token").asText("");
        if (refreshToken.isBlank()) {
            refreshToken = vault.load(role).map(OAuthCredential::refreshToken).orElse("");
        }
        long expiresIn = token.path("expires_in").asLong(3600);
        String email = fetchEmail(accessToken);
        ensureDifferentAccount(role, email);
        OAuthCredential credential = new OAuthCredential(
                accessToken,
                refreshToken,
                Instant.now().plusSeconds(Math.max(60, expiresIn)),
                email,
                token.path("scope").asText(scopeFor(role)));
        vault.save(role, credential);
        return role;
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

    private String createState(AccountRole role) {
        byte[] nonce = new byte[18];
        secureRandom.nextBytes(nonce);
        String payload = role.name() + "." + Instant.now().getEpochSecond() + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return payload + "." + sign(payload);
    }

    private AccountRole verifyState(String state) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException("OAuth state is missing");
        String[] parts = state.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("OAuth state is invalid");
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = parts[3].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("OAuth state signature is invalid");
        long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("OAuth state timestamp is invalid");
        }
        long age = Instant.now().getEpochSecond() - issuedAt;
        if (age < -60 || age > STATE_MAX_AGE_SECONDS) throw new IllegalArgumentException("OAuth state is expired");
        try {
            return AccountRole.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OAuth account role is invalid");
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign OAuth state", e);
        }
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
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth client ID and secret are not configured");
        }
        if (stateSecret == null || stateSecret.length() < 32) {
            throw new IllegalStateException("OAUTH_STATE_SECRET must be configured with at least 32 characters");
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
