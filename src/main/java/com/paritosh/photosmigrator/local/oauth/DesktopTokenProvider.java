package com.paritosh.photosmigrator.local.oauth;

import java.util.concurrent.atomic.AtomicReference;

public class DesktopTokenProvider implements AccessTokenProvider {
    private final DesktopOAuthService oauthService;
    private final AtomicReference<DesktopOAuthToken> token;

    public DesktopTokenProvider(DesktopOAuthService oauthService, DesktopOAuthToken initialToken) {
        this.oauthService = oauthService;
        this.token = new AtomicReference<>(initialToken);
    }

    @Override
    public String accessToken() {
        DesktopOAuthToken current = token.get();
        if (current == null) throw new IllegalStateException("Destination Google account is not authenticated");
        if (!current.isUsable()) current = token.updateAndGet(oauthService::refresh);
        return current.accessToken();
    }

    @Override
    public String refreshAccessToken() {
        DesktopOAuthToken refreshed = token.updateAndGet(oauthService::refresh);
        return refreshed.accessToken();
    }

    public String email() {
        DesktopOAuthToken current = token.get();
        return current == null ? "" : current.email();
    }
}
