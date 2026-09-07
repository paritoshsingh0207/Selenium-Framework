package com.paritosh.photosmigrator.local.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DesktopOAuthService {
    public static final String DESTINATION_SCOPES = String.join(" ",
            "openid",
            "email",
            "https://www.googleapis.com/auth/photoslibrary.appendonly",
            "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata");

    private static final String USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DesktopOAuthClientConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DesktopOAuthService(DesktopOAuthClientConfig config) {
        this(config, new ObjectMapper(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    DesktopOAuthService(DesktopOAuthClientConfig config, ObjectMapper objectMapper, HttpClient httpClient) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public DesktopOAuthToken authorizeDestination() {
        AtomicReference<String> code = new AtomicReference<>();
        AtomicReference<String> callbackError = new AtomicReference<>();
        CountDownLatch callback = new CountDownLatch(1);
        String state = randomUrlToken(24);
        String verifier = randomUrlToken(48);
        String challenge = sha256Base64Url(verifier);
        HttpServer server = null;

        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            String redirectUri = "http://127.0.0.1:" + port + "/oauth/callback";
            String authorizationUrl = authorizationUrl(redirectUri, state, challenge);

            server.createContext("/oauth/callback", exchange -> handleCallback(exchange, state, code, callbackError, callback));
            server.start();
            System.out.println("Opening Google sign-in in your browser...");
            System.out.println("If the browser does not open automatically, open this URL:");
            System.out.println(authorizationUrl);
            openBrowser(authorizationUrl);

            if (!callback.await(5, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Google sign-in timed out after 5 minutes");
            }
            if (callbackError.get() != null && !callbackError.get().isBlank()) {
                throw new IllegalStateException("Google sign-in failed: " + callbackError.get());
            }
            if (code.get() == null || code.get().isBlank()) {
                throw new IllegalStateException("Google sign-in did not return an authorization code");
            }
            return exchangeCode(code.get(), redirectUri, verifier);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start local OAuth callback server", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google sign-in was interrupted", e);
        } finally {
            if (server != null) server.stop(0);
        }
    }

    public DesktopOAuthToken refresh(DesktopOAuthToken current) {
        if (current == null || current.refreshToken() == null || current.refreshToken().isBlank()) {
            throw new IllegalArgumentException("A refresh token is required");
        }
        StringBuilder body = new StringBuilder()
                .append("client_id=").append(enc(config.clientId()))
                .append("&refresh_token=").append(enc(current.refreshToken()))
                .append("&grant_type=refresh_token");
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            body.append("&client_secret=").append(enc(config.clientSecret()));
        }
        JsonNode token = postForm(config.tokenEndpoint(), body.toString(), "Google OAuth refresh failed");
        return new DesktopOAuthToken(
                required(token, "access_token"),
                current.refreshToken(),
                Instant.now().plusSeconds(token.path("expires_in").asLong(3600)),
                current.email(),
                token.path("scope").asText(current.scope()));
    }

    String authorizationUrl(String redirectUri, String state, String codeChallenge) {
        return config.authorizationEndpoint()
                + "?client_id=" + enc(config.clientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&access_type=offline"
                + "&prompt=" + enc("consent select_account")
                + "&scope=" + enc(DESTINATION_SCOPES)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256";
    }

    private DesktopOAuthToken exchangeCode(String code, String redirectUri, String verifier) {
        StringBuilder body = new StringBuilder()
                .append("code=").append(enc(code))
                .append("&client_id=").append(enc(config.clientId()))
                .append("&redirect_uri=").append(enc(redirectUri))
                .append("&grant_type=authorization_code")
                .append("&code_verifier=").append(enc(verifier));
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            body.append("&client_secret=").append(enc(config.clientSecret()));
        }
        JsonNode token = postForm(config.tokenEndpoint(), body.toString(), "Google OAuth token exchange failed");
        String accessToken = required(token, "access_token");
        String refreshToken = token.path("refresh_token").asText("");
        String email = fetchEmail(accessToken);
        return new DesktopOAuthToken(
                accessToken,
                refreshToken,
                Instant.now().plusSeconds(token.path("expires_in").asLong(3600)),
                email,
                token.path("scope").asText(DESTINATION_SCOPES));
    }

    private String fetchEmail(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USERINFO))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        JsonNode json = sendJson(request, "Unable to identify connected Google account");
        return required(json, "email");
    }

    private JsonNode postForm(String url, String body, String message) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return sendJson(request, message);
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

    private void handleCallback(HttpExchange exchange, String expectedState,
                                AtomicReference<String> code,
                                AtomicReference<String> callbackError,
                                CountDownLatch callback) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String returnedState = query.getOrDefault("state", "");
        if (!MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8))) {
            callbackError.set("OAuth state mismatch");
        } else if (query.containsKey("error")) {
            callbackError.set(query.get("error"));
        } else {
            code.set(query.getOrDefault("code", ""));
        }

        String body = callbackError.get() == null
                ? "<html><body><h2>Google Photos account connected.</h2><p>You can close this window and return to Photos Migrator.</p></body></html>"
                : "<html><body><h2>Google sign-in failed.</h2><p>You can close this window and return to Photos Migrator.</p></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(callbackError.get() == null ? 200 : 400, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
        callback.countDown();
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String part : raw.split("&")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = equals < 0 ? "" : part.substring(equals + 1);
            values.put(dec(key), dec(value));
        }
        return values;
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // URL is always printed so browser launch failure is recoverable.
        }
    }

    private String randomUrlToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String sha256Base64Url(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create OAuth PKCE challenge", e);
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("Google response did not contain " + field);
        return value;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String dec(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
