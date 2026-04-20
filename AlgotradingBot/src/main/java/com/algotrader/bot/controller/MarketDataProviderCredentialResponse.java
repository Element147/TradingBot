package com.algotrader.bot.controller;

import java.time.LocalDateTime;

public record MarketDataProviderCredentialResponse(
    String providerId,
    String providerLabel,
    String apiKeyEnvironmentVariable,
    boolean apiKeyRequired,
    boolean hasStoredCredential,
    boolean hasEnvironmentCredential,
    boolean effectiveCredentialConfigured,
    String credentialSource,
    boolean storageEncryptionConfigured,
    String note,
    LocalDateTime updatedAt,
    String docsUrl,
    String signupUrl,
    String accountNotes
) {
}
