package com.paritosh.photosmigrator.local.oauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopOAuthServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsDesktopClientCredentialsAndBuildsPkceAuthorizationUrl() throws Exception {
        Path credentials = tempDir.resolve("credentials.json");
        Files.writeString(credentials, """
                {"installed":{
                  "client_id":"client-123.apps.googleusercontent.com",
                  "client_secret":"secret",
                  "auth_uri":"https://accounts.google.com/o/oauth2/v2/auth",
                  "token_uri":"https://oauth2.googleapis.com/token"
                }}
                """);

        DesktopOAuthClientConfig config = DesktopOAuthClientConfig.fromJson(credentials);
        DesktopOAuthService service = new DesktopOAuthService(config);
        String url = service.authorizationUrl("http://127.0.0.1:12345/oauth/callback", "state123", "challenge123");

        assertThat(config.clientId()).isEqualTo("client-123.apps.googleusercontent.com");
        assertThat(url).contains("response_type=code")
                .contains("code_challenge=challenge123")
                .contains("photoslibrary.appendonly")
                .contains("photoslibrary.readonly.appcreateddata")
                .contains("prompt=consent+select_account");
    }
}
