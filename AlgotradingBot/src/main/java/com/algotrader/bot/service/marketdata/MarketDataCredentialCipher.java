package com.algotrader.bot.service.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class MarketDataCredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String VERSION_V1 = "AES_GCM_V1";
    private static final String VERSION_V2 = "AES_GCM_V2";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String masterKey;

    public MarketDataCredentialCipher(
        @Value("${algotrading.market-data.credentials.master-key:}") String masterKey
    ) {
        this.masterKey = masterKey == null ? "" : masterKey.trim();
    }

    public boolean isConfigured() {
        return !masterKey.isBlank();
    }

    public String version() {
        return VERSION_V2;
    }

    public EncryptedValue encrypt(String plaintext) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Set ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY before saving encrypted market-data API keys."
            );
        }
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("API key cannot be blank.");
        }

        try {
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(salt);
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.trim().getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[SALT_LENGTH_BYTES + encrypted.length];
            System.arraycopy(salt, 0, payload, 0, SALT_LENGTH_BYTES);
            System.arraycopy(encrypted, 0, payload, SALT_LENGTH_BYTES, encrypted.length);

            return new EncryptedValue(
                Base64.getEncoder().encodeToString(payload),
                Base64.getEncoder().encodeToString(iv),
                VERSION_V2
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt market-data API key.", exception);
        }
    }

    public String decrypt(String ciphertext, String iv, String version) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "Stored market-data API keys cannot be decrypted until ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY is set."
            );
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] decrypted;

            if (VERSION_V2.equals(version)) {
                byte[] payload = Base64.getDecoder().decode(ciphertext);
                if (payload.length <= SALT_LENGTH_BYTES) {
                    throw new IllegalStateException("Encrypted market-data credential payload is invalid.");
                }

                byte[] salt = Arrays.copyOfRange(payload, 0, SALT_LENGTH_BYTES);
                byte[] encrypted = Arrays.copyOfRange(payload, SALT_LENGTH_BYTES, payload.length);
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, ivBytes));
                decrypted = cipher.doFinal(encrypted);
            } else if (VERSION_V1.equals(version)) {
                cipher.init(Cipher.DECRYPT_MODE, deriveLegacyKey(), new GCMParameterSpec(GCM_TAG_BITS, ivBytes));
                decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            } else {
                throw new IllegalStateException("Unsupported market-data credential encryption version: " + version);
            }

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt stored market-data API key.", exception);
        }
    }

    private SecretKeySpec deriveKey(byte[] salt) throws Exception {
        char[] password = masterKey.toCharArray();
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .getEncoded();
            return new SecretKeySpec(encoded, KEY_ALGORITHM);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private SecretKeySpec deriveLegacyKey() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, KEY_ALGORITHM);
    }

    public record EncryptedValue(
        String ciphertext,
        String iv,
        String version
    ) {
    }
}
