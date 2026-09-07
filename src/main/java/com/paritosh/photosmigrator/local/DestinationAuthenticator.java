package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;

public interface DestinationAuthenticator {
    AuthenticatedDestination authenticate(String expectedAccountEmail);

    record AuthenticatedDestination(String email, AccessTokenProvider tokens) { }
}
