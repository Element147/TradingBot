package com.algotrader.bot.service.marketdata;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketDataCredentialCipherTest {

    private static final String MASTER_KEY = "market-data-secret";

    @Test
    void encryptAndDecryptRoundTripUsesCurrentVersion() {
        MarketDataCredentialCipher cipher = new MarketDataCredentialCipher(MASTER_KEY);

        MarketDataCredentialCipher.EncryptedValue encrypted = cipher.encrypt("api-key-value");

        assertEquals("AES_GCM_V2", encrypted.version());
        assertEquals("api-key-value", cipher.decrypt(encrypted.ciphertext(), encrypted.iv(), encrypted.version()));
    }

    @Test
    void decryptSupportsLegacyVersionOnePayloads() throws Exception {
        MarketDataCredentialCipher cipher = new MarketDataCredentialCipher(MASTER_KEY);
        MarketDataCredentialCipher.EncryptedValue legacy = legacyEncrypt("legacy-api-key", MASTER_KEY);

        assertEquals("legacy-api-key", cipher.decrypt(legacy.ciphertext(), legacy.iv(), legacy.version()));
    }

    @Test
    void encryptRequiresConfiguredMasterKey() {
        MarketDataCredentialCipher cipher = new MarketDataCredentialCipher("");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("api-key-value"));
    }

    private MarketDataCredentialCipher.EncryptedValue legacyEncrypt(String plaintext, String masterKey) throws Exception {
        byte[] iv = new byte[12];
        SecretKeySpec key = new SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(masterKey.getBytes(StandardCharsets.UTF_8)),
            "AES"
        );

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return new MarketDataCredentialCipher.EncryptedValue(
            Base64.getEncoder().encodeToString(encrypted),
            Base64.getEncoder().encodeToString(iv),
            "AES_GCM_V1"
        );
    }
}
