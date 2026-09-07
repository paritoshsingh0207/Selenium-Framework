package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.oauth.DesktopOAuthClientConfig;
import com.paritosh.photosmigrator.local.oauth.DesktopOAuthService;
import com.paritosh.photosmigrator.local.oauth.DesktopOAuthToken;
import com.paritosh.photosmigrator.local.oauth.DesktopTokenProvider;

public class DesktopDestinationAuthenticator implements DestinationAuthenticator {
    private final DesktopOAuthClientConfig config;

    public DesktopDestinationAuthenticator(DesktopOAuthClientConfig config) {
        this.config = config;
    }

    @Override
    public AuthenticatedDestination authenticate(String expectedAccountEmail) {
        DesktopOAuthService oauth = new DesktopOAuthService(config);
        DesktopOAuthToken token = oauth.authorizeDestination();
        if (!expectedAccountEmail.equalsIgnoreCase(token.email())) {
            throw new IllegalArgumentException(
                    "Wrong Google account selected. Expected " + expectedAccountEmail + " but connected " + token.email());
        }
        return new AuthenticatedDestination(token.email(), new DesktopTokenProvider(oauth, token));
    }
}
