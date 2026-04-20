package com.algotrader.bot.service;

import com.algotrader.bot.controller.OperatorAuditEventListResponse;
import com.algotrader.bot.controller.OperatorAuditEventResponse;
import com.algotrader.bot.controller.OperatorAuditSummaryResponse;
import com.algotrader.bot.entity.OperatorAuditEvent;
import com.algotrader.bot.repository.OperatorAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class OperatorAuditService {

    private static final Logger logger = LoggerFactory.getLogger(OperatorAuditService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_DETAILS_LENGTH = 2000;

    private final OperatorAuditEventRepository operatorAuditEventRepository;

    public OperatorAuditService(OperatorAuditEventRepository operatorAuditEventRepository) {
        this.operatorAuditEventRepository = operatorAuditEventRepository;
    }

    @Transactional
    public void recordSuccess(
        String action,
        String environment,
        String targetType,
        String targetId,
        String details
    ) {
        persistEvent(action, environment, targetType, targetId, "SUCCESS", details);
    }

    @Transactional
    public void recordFailure(
        String action,
        String environment,
        String targetType,
        String targetId,
        String details
    ) {
        persistEvent(action, environment, targetType, targetId, "FAILED", details);
    }

    @Transactional(readOnly = true)
    public OperatorAuditEventListResponse listEvents(
        int requestedLimit,
        String environment,
        String outcome,
        String targetType,
        String search
    ) {
        int limit = sanitizeLimit(requestedLimit);
        Specification<OperatorAuditEvent> specification = buildSpecification(environment, outcome, targetType, search);
        var pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<OperatorAuditEventResponse> events = operatorAuditEventRepository.findAll(specification, pageable).stream()
            .map(event -> new OperatorAuditEventResponse(
                event.getId(),
                event.getActor(),
                event.getAction(),
                event.getEnvironment(),
                event.getTargetType(),
                event.getTargetId(),
                event.getOutcome(),
                event.getDetails(),
                event.getCreatedAt()
            ))
            .toList();

        long totalMatchingEvents = operatorAuditEventRepository.count(specification);
        return new OperatorAuditEventListResponse(
            summarize(events, totalMatchingEvents),
            events
        );
    }

    private void persistEvent(
        String action,
        String environment,
        String targetType,
        String targetId,
        String outcome,
        String details
    ) {
        try {
            OperatorAuditEvent event = new OperatorAuditEvent();
            event.setActor(resolveActor());
            event.setAction(normalize(action, "UNKNOWN_ACTION"));
            event.setEnvironment(normalizeEnvironment(environment));
            event.setTargetType(normalize(targetType, "UNKNOWN_TARGET"));
            event.setTargetId(trimToNull(targetId));
            event.setOutcome(normalize(outcome, "UNKNOWN"));
            event.setDetails(trimToMax(details, MAX_DETAILS_LENGTH));

            operatorAuditEventRepository.save(event);
        } catch (Exception exception) {
            logger.warn("Unable to persist operator audit event for action {}", action, exception);
        }
    }

    private String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return "system";
        }
        return name;
    }

    private int sanitizeLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private Specification<OperatorAuditEvent> buildSpecification(
        String environment,
        String outcome,
        String targetType,
        String search
    ) {
        Specification<OperatorAuditEvent> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        String normalizedEnvironment = trimToNull(environment);
        if (normalizedEnvironment != null) {
            specification = specification.and(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("environment")),
                        normalizedEnvironment.toLowerCase(Locale.ROOT)
                    )
            );
        }

        String normalizedOutcome = trimToNull(outcome);
        if (normalizedOutcome != null) {
            specification = specification.and(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("outcome")),
                        normalizedOutcome.toUpperCase(Locale.ROOT)
                    )
            );
        }

        String normalizedTargetType = trimToNull(targetType);
        if (normalizedTargetType != null) {
            specification = specification.and(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("targetType")),
                        normalizedTargetType.toUpperCase(Locale.ROOT)
                    )
            );
        }

        String normalizedSearch = trimToNull(search);
        if (normalizedSearch != null) {
            String likePattern = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("actor")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("action")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("environment")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("targetType")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("targetId"), "")), likePattern),
                criteriaBuilder.like(criteriaBuilder.lower(criteriaBuilder.coalesce(root.get("details"), "")), likePattern)
            ));
        }

        return specification;
    }

    private OperatorAuditSummaryResponse summarize(List<OperatorAuditEventResponse> events, long totalMatchingEvents) {
        int successCount = (int) events.stream()
            .filter(event -> "SUCCESS".equalsIgnoreCase(event.outcome()))
            .count();
        int failedCount = (int) events.stream()
            .filter(event -> !"SUCCESS".equalsIgnoreCase(event.outcome()))
            .count();

        return new OperatorAuditSummaryResponse(
            events.size(),
            totalMatchingEvents,
            successCount,
            failedCount,
            (int) events.stream().map(OperatorAuditEventResponse::actor).filter(Objects::nonNull).distinct().count(),
            (int) events.stream().map(OperatorAuditEventResponse::action).filter(Objects::nonNull).distinct().count(),
            (int) events.stream().filter(event -> "test".equalsIgnoreCase(event.environment())).count(),
            (int) events.stream().filter(event -> "paper".equalsIgnoreCase(event.environment())).count(),
            (int) events.stream().filter(event -> "live".equalsIgnoreCase(event.environment())).count(),
            events.stream().map(OperatorAuditEventResponse::createdAt).filter(Objects::nonNull).max(java.time.LocalDateTime::compareTo).orElse(null)
        );
    }

    private String normalizeEnvironment(String environment) {
        String normalized = normalize(environment, "test").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "paper", "live", "test" -> normalized;
            default -> "test";
        };
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToMax(String value, int maxLength) {
        String sanitized = trimToNull(value);
        if (sanitized == null) {
            return null;
        }
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }
}
