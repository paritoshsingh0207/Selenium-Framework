package com.paritosh.photosmigrator.oauth;

import com.paritosh.photosmigrator.model.AccountRole;

import java.util.Optional;

public interface CredentialVault {
    Optional<OAuthCredential> load(AccountRole role);
    void save(AccountRole role, OAuthCredential credential);
    void clear(AccountRole role);
}
