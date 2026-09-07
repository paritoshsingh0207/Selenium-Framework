package com.paritosh.photosmigrator.oauth;

import java.time.Instant;

public record OAuthCredential(
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
