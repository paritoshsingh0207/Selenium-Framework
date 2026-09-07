package com.paritosh.photosmigrator.local.oauth;

public interface AccessTokenProvider {
    String accessToken();

    String refreshAccessToken();
}
