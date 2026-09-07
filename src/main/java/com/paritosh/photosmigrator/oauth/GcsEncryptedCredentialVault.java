package com.paritosh.photosmigrator.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.paritosh.photosmigrator.model.AccountRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.google.credential-vault-mode", havingValue = "gcs")
public class GcsEncryptedCredentialVault implements CredentialVault {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();
    private final ObjectMapper objectMapper;
    private final String bucketName;
    private final String objectPrefix;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public GcsEncryptedCredentialVault(
            ObjectMapper objectMapper,
            @Value("${app.google.credential-vault-bucket:}") String bucketName,
            @Value("${app.google.credential-vault-prefix:oauth}") String objectPrefix,
            @Value("${app.google.credential-vault-key-base64:}") String keyBase64) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("OAUTH_VAULT_BUCKET is required when credential vault mode is gcs");
        }
        byte[] keyBytes = decodeKey(keyBase64);
        this.objectMapper = objectMapper;
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public Optional<OAuthCredential> load(AccountRole role) {
        Blob blob = storage.get(blobId(role));
        if (blob == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(decrypt(blob.getContent()), OAuthCredential.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt OAuth credential for " + role, e);
        }
    }

    @Override
    public void save(AccountRole role, OAuthCredential credential) {
        try {
            byte[] encrypted = encrypt(objectMapper.writeValueAsBytes(credential));
            BlobInfo info = BlobInfo.newBuilder(blobId(role)).setContentType("application/octet-stream").build();
            storage.create(info, encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist OAuth credential for " + role, e);
        }
    }

    @Override
    public void clear(AccountRole role) {
        storage.delete(blobId(role));
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(plain);
        byte[] output = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(cipherText, 0, output, iv.length, cipherText.length);
        return output;
    }

    private byte[] decrypt(byte[] encrypted) throws Exception {
        if (encrypted.length <= IV_LENGTH) throw new IllegalArgumentException("Invalid encrypted credential");
        byte[] iv = Arrays.copyOfRange(encrypted, 0, IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(encrypted, IV_LENGTH, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    private BlobId blobId(AccountRole role) {
        return BlobId.of(bucketName, objectPrefix + "/" + role.name().toLowerCase() + ".credential.enc");
    }

    private byte[] decodeKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OAUTH_VAULT_KEY_BASE64 is required when credential vault mode is gcs");
        }
        byte[] keyBytes = Base64.getDecoder().decode(value);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("OAUTH_VAULT_KEY_BASE64 must decode to exactly 32 bytes");
        }
        return keyBytes;
    }
}
