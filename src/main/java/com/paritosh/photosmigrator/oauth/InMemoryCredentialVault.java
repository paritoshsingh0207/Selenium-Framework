package com.paritosh.photosmigrator.oauth;

import com.paritosh.photosmigrator.model.AccountRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.google.credential-vault-mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryCredentialVault implements CredentialVault {
    private final ConcurrentHashMap<AccountRole, OAuthCredential> credentials = new ConcurrentHashMap<>();

    @Override
    public Optional<OAuthCredential> load(AccountRole role) {
        return Optional.ofNullable(credentials.get(role));
    }

    @Override
    public void save(AccountRole role, OAuthCredential credential) {
        credentials.put(role, credential);
    }

    @Override
    public void clear(AccountRole role) {
        credentials.remove(role);
    }
}
