package ru.kondrashov.personservice.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field encryption for personal data stored by PersonService.
 * The key is supplied at runtime (e.g. from KMS/Vault via PERSON_PII_ENCRYPTION_KEY) and never committed.
 * Stored format: {@code v1:<base64(iv || ciphertext || tag)>}.
 */
@Component
public class PiiCipher {

    static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher(@Value("${person.pii.encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new PiiEncryptionException("Failed to encrypt personal data", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            throw new PiiEncryptionException("Stored personal data is not in the expected encrypted format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new PiiEncryptionException("Stored personal data is truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new PiiEncryptionException("Failed to decrypt personal data", e);
        }
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.trim().isEmpty()) {
            throw new IllegalStateException(
                    "person.pii.encryption-key is not set; provide a base64-encoded 256-bit key via PERSON_PII_ENCRYPTION_KEY");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("person.pii.encryption-key must be base64-encoded", e);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("person.pii.encryption-key must decode to exactly 32 bytes (AES-256)");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
