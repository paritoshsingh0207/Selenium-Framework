package com.paritosh.photosmigrator.local.oauth;

import java.time.Instant;

public record DesktopOAuthToken(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String email,
        String scope
) {
    public boolean isUsable() {
        return accessToken != null && !accessToken.isBlank()
                && expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(60));
    }
}
