package com.algotrader.bot.service.marketdata;

import com.algotrader.bot.controller.MarketDataProviderCredentialRequest;
import com.algotrader.bot.controller.MarketDataProviderCredentialResponse;
import com.algotrader.bot.entity.MarketDataProviderCredential;
import com.algotrader.bot.repository.MarketDataProviderCredentialRepository;
import com.algotrader.bot.service.OperatorAuditService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MarketDataProviderCredentialService {

    private final MarketDataProviderCredentialRepository marketDataProviderCredentialRepository;
    private final MarketDataCredentialCipher marketDataCredentialCipher;
    private final Environment environment;
    private final OperatorAuditService operatorAuditService;

    public MarketDataProviderCredentialService(
        MarketDataProviderCredentialRepository marketDataProviderCredentialRepository,
        MarketDataCredentialCipher marketDataCredentialCipher,
        Environment environment,
        OperatorAuditService operatorAuditService
    ) {
        this.marketDataProviderCredentialRepository = marketDataProviderCredentialRepository;
        this.marketDataCredentialCipher = marketDataCredentialCipher;
        this.environment = environment;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(MarketDataProviderDefinition definition) {
        return resolveCredentialSource(definition) == MarketDataCredentialSource.NOT_REQUIRED
            || resolveCredentialSource(definition) == MarketDataCredentialSource.DATABASE
            || resolveCredentialSource(definition) == MarketDataCredentialSource.ENVIRONMENT;
    }

    @Transactional(readOnly = true)
    public MarketDataCredentialSource resolveCredentialSource(MarketDataProviderDefinition definition) {
        if (!definition.apiKeyRequired()) {
            return MarketDataCredentialSource.NOT_REQUIRED;
        }

        boolean hasStoredCredential = marketDataProviderCredentialRepository.findByProviderId(definition.id()).isPresent();
        boolean hasEnvironmentCredential = hasEnvironmentCredential(definition);

        if (hasStoredCredential) {
            if (marketDataCredentialCipher.isConfigured()) {
                return MarketDataCredentialSource.DATABASE;
            }
            return hasEnvironmentCredential ? MarketDataCredentialSource.ENVIRONMENT : MarketDataCredentialSource.DATABASE_LOCKED;
        }

        return hasEnvironmentCredential ? MarketDataCredentialSource.ENVIRONMENT : MarketDataCredentialSource.NONE;
    }

    @Transactional(readOnly = true)
    public String requiredApiKey(MarketDataProviderDefinition definition) {
        if (!definition.apiKeyRequired()) {
            throw new IllegalStateException(definition.label() + " does not require an API key.");
        }

        Optional<MarketDataProviderCredential> storedCredential = marketDataProviderCredentialRepository.findByProviderId(definition.id());
        if (storedCredential.isPresent() && marketDataCredentialCipher.isConfigured()) {
            return marketDataCredentialCipher.decrypt(
                storedCredential.get().getEncryptedApiKey(),
                storedCredential.get().getEncryptionIv(),
                storedCredential.get().getEncryptionVersion()
            );
        }

        String environmentApiKey = readEnvironmentCredential(definition);
        if (environmentApiKey != null) {
            return environmentApiKey;
        }

        if (storedCredential.isPresent()) {
            throw new IllegalArgumentException(
                definition.label()
                    + " has a stored database credential, but ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY is not configured on this backend."
            );
        }

        throw new IllegalArgumentException(missingCredentialMessage(definition));
    }

    @Transactional(readOnly = true)
    public List<MarketDataProviderCredentialResponse> listCredentialSettings(List<MarketDataProviderDefinition> definitions) {
        return definitions.stream()
            .filter(MarketDataProviderDefinition::apiKeyRequired)
            .sorted(Comparator.comparing(MarketDataProviderDefinition::label))
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public MarketDataProviderCredentialResponse saveCredential(
        MarketDataProviderDefinition definition,
        MarketDataProviderCredentialRequest request
    ) {
        validateStorableProvider(definition);
        if (!marketDataCredentialCipher.isConfigured()) {
            throw new IllegalStateException(
                "Set ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY before saving encrypted market-data API keys."
            );
        }

        String apiKey = trimToNull(request.apiKey());
        String note = trimToNull(request.note());
        MarketDataProviderCredential credential = marketDataProviderCredentialRepository.findByProviderId(definition.id())
            .orElseGet(MarketDataProviderCredential::new);

        if (credential.getId() == null && apiKey == null) {
            throw new IllegalArgumentException("Enter an API key before saving a new provider credential.");
        }

        if (apiKey != null) {
            MarketDataCredentialCipher.EncryptedValue encryptedValue = marketDataCredentialCipher.encrypt(apiKey);
            credential.setProviderId(definition.id());
            credential.setEncryptedApiKey(encryptedValue.ciphertext());
            credential.setEncryptionIv(encryptedValue.iv());
            credential.setEncryptionVersion(encryptedValue.version());
        }

        credential.setProviderId(definition.id());
        credential.setNote(note);
        MarketDataProviderCredential saved = marketDataProviderCredentialRepository.save(credential);

        operatorAuditService.recordSuccess(
            "MARKET_DATA_CREDENTIAL_SAVED",
            "test",
            "MARKET_DATA_PROVIDER_CREDENTIAL",
            definition.id(),
            "provider=" + definition.id() + ", noteUpdated=" + (note != null)
        );
        return toResponse(definition, saved);
    }

    @Transactional
    public void deleteCredential(MarketDataProviderDefinition definition) {
        validateStorableProvider(definition);
        marketDataProviderCredentialRepository.findByProviderId(definition.id()).ifPresent(credential -> {
            marketDataProviderCredentialRepository.delete(credential);
            operatorAuditService.recordSuccess(
                "MARKET_DATA_CREDENTIAL_DELETED",
                "test",
                "MARKET_DATA_PROVIDER_CREDENTIAL",
                definition.id(),
                "provider=" + definition.id()
            );
        });
    }

    public String missingCredentialMessage(MarketDataProviderDefinition definition) {
        return definition.label() + " requires an API key configured in Settings > API Config or via "
            + definition.apiKeyEnvironmentVariable() + ".";
    }

    private MarketDataProviderCredentialResponse toResponse(MarketDataProviderDefinition definition) {
        return toResponse(definition, marketDataProviderCredentialRepository.findByProviderId(definition.id()).orElse(null));
    }

    private MarketDataProviderCredentialResponse toResponse(
        MarketDataProviderDefinition definition,
        MarketDataProviderCredential credential
    ) {
        boolean hasStoredCredential = credential != null;
        boolean hasEnvironmentCredential = hasEnvironmentCredential(definition);
        MarketDataCredentialSource source = resolveCredentialSource(definition);

        return new MarketDataProviderCredentialResponse(
            definition.id(),
            definition.label(),
            definition.apiKeyEnvironmentVariable(),
            definition.apiKeyRequired(),
            hasStoredCredential,
            hasEnvironmentCredential,
            source == MarketDataCredentialSource.DATABASE
                || source == MarketDataCredentialSource.ENVIRONMENT
                || source == MarketDataCredentialSource.NOT_REQUIRED,
            source.name(),
            marketDataCredentialCipher.isConfigured(),
            credential == null ? null : credential.getNote(),
            credential == null ? null : credential.getUpdatedAt(),
            definition.docsUrl(),
            definition.signupUrl(),
            definition.accountNotes()
        );
    }

    private boolean hasEnvironmentCredential(MarketDataProviderDefinition definition) {
        return readEnvironmentCredential(definition) != null;
    }

    private String readEnvironmentCredential(MarketDataProviderDefinition definition) {
        String environmentVariable = definition.apiKeyEnvironmentVariable();
        if (environmentVariable == null || environmentVariable.isBlank()) {
            return null;
        }

        String value = environment.getProperty(environmentVariable);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateStorableProvider(MarketDataProviderDefinition definition) {
        if (!definition.apiKeyRequired()) {
            throw new IllegalArgumentException(definition.label() + " does not use an API key.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
