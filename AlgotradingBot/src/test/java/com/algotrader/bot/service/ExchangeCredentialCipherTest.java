package com.algotrader.bot.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeCredentialCipherTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    void encryptAndDecryptRoundTripUsesCurrentFormat() {
        ExchangeCredentialCipher cipher = new ExchangeCredentialCipher(SECRET);

        String encrypted = cipher.encrypt("top-secret");

        assertTrue(encrypted.startsWith("AES_GCM_PBKDF2_V2:"));
        assertEquals("top-secret", cipher.decrypt(encrypted));
    }

    @Test
    void decryptSupportsLegacyPayloads() throws Exception {
        ExchangeCredentialCipher cipher = new ExchangeCredentialCipher(SECRET);

        String legacyPayload = legacyEncrypt("legacy-value", SECRET);

        assertEquals("legacy-value", cipher.decrypt(legacyPayload));
    }

    private String legacyEncrypt(String plaintext, String secret) throws Exception {
        byte[] iv = new byte[12];
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)),
            "AES"
        );

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(payload);
    }
}
