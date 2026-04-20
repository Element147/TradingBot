package com.algotrader.bot.service;

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
public class ExchangeCredentialCipher {

    private static final String CURRENT_VERSION = "AES_GCM_PBKDF2_V2";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;

    private final String secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExchangeCredentialCipher(
        @Value("${algotrading.security.connection-encryption-key:${jwt.secret}}") String secret
    ) {
        this.secret = secret == null ? "" : secret;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }

        try {
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(salt);
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return CURRENT_VERSION
                + ":"
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(iv)
                + ":"
                + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt exchange credential", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }

        try {
            if (ciphertext.startsWith(CURRENT_VERSION + ":")) {
                String[] parts = ciphertext.split(":");
                if (parts.length != 4) {
                    throw new IllegalStateException("Encrypted credential payload is invalid");
                }

                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] iv = Base64.getDecoder().decode(parts[2]);
                byte[] encrypted = Base64.getDecoder().decode(parts[3]);

                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            }

            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalStateException("Encrypted credential payload is invalid");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveLegacyKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt exchange credential", exception);
        }
    }

    private SecretKeySpec deriveKey(byte[] salt) {
        char[] password = secret.toCharArray();
        try {
            PBEKeySpec keySpec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .getEncoded();
            return new SecretKeySpec(encoded, KEY_ALGORITHM);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive credential encryption key", exception);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private SecretKeySpec deriveLegacyKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), KEY_ALGORITHM);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to derive legacy credential encryption key", exception);
        }
    }
}
