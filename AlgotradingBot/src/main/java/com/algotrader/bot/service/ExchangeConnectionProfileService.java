package com.algotrader.bot.service;

import com.algotrader.bot.controller.ExchangeConnectionProfileRequest;
import com.algotrader.bot.controller.ExchangeConnectionProfileResponse;
import com.algotrader.bot.controller.ExchangeConnectionProfilesResponse;
import com.algotrader.bot.entity.ExchangeConnectionProfile;
import com.algotrader.bot.entity.User;
import com.algotrader.bot.repository.ExchangeConnectionProfileRepository;
import com.algotrader.bot.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExchangeConnectionProfileService {

    private final ExchangeConnectionProfileRepository exchangeConnectionProfileRepository;
    private final UserRepository userRepository;
    private final ExchangeCredentialCipher exchangeCredentialCipher;
    private final OperatorAuditService operatorAuditService;

    public ExchangeConnectionProfileService(
        ExchangeConnectionProfileRepository exchangeConnectionProfileRepository,
        UserRepository userRepository,
        ExchangeCredentialCipher exchangeCredentialCipher,
        OperatorAuditService operatorAuditService
    ) {
        this.exchangeConnectionProfileRepository = exchangeConnectionProfileRepository;
        this.userRepository = userRepository;
        this.exchangeCredentialCipher = exchangeCredentialCipher;
        this.operatorAuditService = operatorAuditService;
    }

    @Transactional(readOnly = true)
    public ExchangeConnectionProfilesResponse listConnectionsForCurrentUser() {
        User user = requireCurrentUser();
        return toListResponse(exchangeConnectionProfileRepository.findByUserOrderByCreatedAtAsc(user));
    }

    @Transactional
    public ExchangeConnectionProfileResponse createConnectionForCurrentUser(ExchangeConnectionProfileRequest request) {
        User user = requireCurrentUser();
        List<ExchangeConnectionProfile> existingConnections =
            exchangeConnectionProfileRepository.findByUserOrderByCreatedAtAsc(user);

        ExchangeConnectionProfile profile = new ExchangeConnectionProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setUser(user);
        profile.setActive(false);
        applyRequest(profile, request, existingConnections);

        ExchangeConnectionProfile saved = exchangeConnectionProfileRepository.save(profile);
        operatorAuditService.recordSuccess(
            "EXCHANGE_CONNECTION_SAVED",
            environmentFor(saved),
            "EXCHANGE_CONNECTION",
            saved.getId(),
            saved.getName()
        );
        return toResponse(saved);
    }

    @Transactional
    public ExchangeConnectionProfileResponse updateConnectionForCurrentUser(
        String connectionId,
        ExchangeConnectionProfileRequest request
    ) {
        User user = requireCurrentUser();
        ExchangeConnectionProfile profile = requireConnection(user, connectionId);
        List<ExchangeConnectionProfile> existingConnections =
            exchangeConnectionProfileRepository.findByUserOrderByCreatedAtAsc(user);

        applyRequest(profile, request, existingConnections);

        ExchangeConnectionProfile saved = exchangeConnectionProfileRepository.save(profile);
        operatorAuditService.recordSuccess(
            "EXCHANGE_CONNECTION_UPDATED",
            environmentFor(saved),
            "EXCHANGE_CONNECTION",
            saved.getId(),
            saved.getName()
        );
        return toResponse(saved);
    }

    @Transactional
    public ExchangeConnectionProfileResponse activateConnectionForCurrentUser(String connectionId) {
        User user = requireCurrentUser();
        List<ExchangeConnectionProfile> connections =
            exchangeConnectionProfileRepository.findByUserOrderByCreatedAtAsc(user);
        ExchangeConnectionProfile target = connections.stream()
            .filter(connection -> Objects.equals(connection.getId(), connectionId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Exchange connection not found: " + connectionId));

        connections.forEach(connection -> connection.setActive(Objects.equals(connection.getId(), connectionId)));
        exchangeConnectionProfileRepository.saveAll(connections);

        operatorAuditService.recordSuccess(
            "EXCHANGE_CONNECTION_ACTIVATED",
            environmentFor(target),
            "EXCHANGE_CONNECTION",
            target.getId(),
            target.getName()
        );
        return toResponse(target);
    }

    @Transactional
    public void deleteConnectionForCurrentUser(String connectionId) {
        User user = requireCurrentUser();
        ExchangeConnectionProfile profile = requireConnection(user, connectionId);
        boolean wasActive = Boolean.TRUE.equals(profile.getActive());
        String environment = environmentFor(profile);
        String name = profile.getName();

        exchangeConnectionProfileRepository.delete(profile);

        if (wasActive) {
            List<ExchangeConnectionProfile> remainingConnections =
                exchangeConnectionProfileRepository.findByUserOrderByCreatedAtAsc(user);
            if (!remainingConnections.isEmpty()) {
                ExchangeConnectionProfile nextActive = remainingConnections.get(0);
                nextActive.setActive(true);
                exchangeConnectionProfileRepository.save(nextActive);
            }
        }

        operatorAuditService.recordSuccess(
            "EXCHANGE_CONNECTION_DELETED",
            environment,
            "EXCHANGE_CONNECTION",
            connectionId,
            name
        );
    }

    private void applyRequest(
        ExchangeConnectionProfile profile,
        ExchangeConnectionProfileRequest request,
        List<ExchangeConnectionProfile> existingConnections
    ) {
        String exchange = normalizeExchange(request.exchange());
        profile.setName(normalizeName(request.name(), exchange, existingConnections, profile.getId()));
        profile.setExchangeName(exchange);
        profile.setApiKeyEncrypted(exchangeCredentialCipher.encrypt(trimToEmpty(request.apiKey())));
        profile.setApiSecretEncrypted(exchangeCredentialCipher.encrypt(trimToEmpty(request.apiSecret())));
        profile.setTestnet(request.testnet() == null || request.testnet());
    }

    private ExchangeConnectionProfile requireConnection(User user, String connectionId) {
        return exchangeConnectionProfileRepository.findByUserAndId(user, connectionId)
            .orElseThrow(() -> new EntityNotFoundException("Exchange connection not found: " + connectionId));
    }

    private User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || authentication instanceof AnonymousAuthenticationToken
            || authentication.getName() == null
            || authentication.getName().isBlank()) {
            throw new UsernameNotFoundException("Authenticated user not found");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private ExchangeConnectionProfilesResponse toListResponse(List<ExchangeConnectionProfile> connections) {
        List<ExchangeConnectionProfileResponse> items = connections.stream()
            .map(this::toResponse)
            .toList();

        String activeConnectionId = connections.stream()
            .filter(connection -> Boolean.TRUE.equals(connection.getActive()))
            .map(ExchangeConnectionProfile::getId)
            .findFirst()
            .orElse(null);

        return new ExchangeConnectionProfilesResponse(items, activeConnectionId);
    }

    private ExchangeConnectionProfileResponse toResponse(ExchangeConnectionProfile profile) {
        return new ExchangeConnectionProfileResponse(
            profile.getId(),
            profile.getName(),
            profile.getExchangeName(),
            exchangeCredentialCipher.decrypt(profile.getApiKeyEncrypted()),
            exchangeCredentialCipher.decrypt(profile.getApiSecretEncrypted()),
            Boolean.TRUE.equals(profile.getTestnet()),
            Boolean.TRUE.equals(profile.getActive()),
            profile.getUpdatedAt()
        );
    }

    private String normalizeExchange(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return "binance";
        }
        return exchange.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(
        String name,
        String exchange,
        List<ExchangeConnectionProfile> existingConnections,
        String currentId
    ) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

        long sameExchangeCount = existingConnections.stream()
            .filter(connection -> Objects.equals(connection.getExchangeName(), exchange))
            .filter(connection -> !Objects.equals(connection.getId(), currentId))
            .count();

        return exchangeLabel(exchange) + " Connection " + (sameExchangeCount + 1);
    }

    private String exchangeLabel(String exchange) {
        return switch (exchange) {
            case "okx" -> "OKX";
            case "kucoin" -> "KuCoin";
            default -> exchange.isEmpty()
                ? "Exchange"
                : exchange.substring(0, 1).toUpperCase(Locale.ROOT) + exchange.substring(1);
        };
    }

    private String environmentFor(ExchangeConnectionProfile profile) {
        return Boolean.TRUE.equals(profile.getTestnet()) ? "test" : "live";
    }

    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
