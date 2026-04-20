package com.algotrader.bot.service;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EnvironmentRequestResolver {

    public static final String TEST = "test";
    public static final String LIVE = "live";
    public static final String RESEARCH = "research";
    public static final String FORWARD_TEST = "forward-test";
    public static final String PAPER = "paper";

    public String resolve(String queryEnvironment, String headerEnvironment) {
        return resolve(queryEnvironment, headerEnvironment, null, null);
    }

    public String resolve(
        String queryEnvironment,
        String headerEnvironment,
        String queryExecutionContext,
        String headerExecutionContext
    ) {
        String rawExecutionContext = hasText(queryExecutionContext) ? queryExecutionContext : headerExecutionContext;
        if (hasText(rawExecutionContext)) {
            return resolveExecutionContext(rawExecutionContext);
        }

        String rawEnvironment = hasText(queryEnvironment) ? queryEnvironment : headerEnvironment;
        if (!hasText(rawEnvironment)) {
            return TEST;
        }

        String normalized = rawEnvironment.trim().toLowerCase(Locale.ROOT);
        if (!TEST.equals(normalized) && !LIVE.equals(normalized)) {
            throw new IllegalArgumentException(
                "Unsupported environment '" + rawEnvironment + "'. Expected 'test' or 'live'."
            );
        }

        return normalized;
    }

    private String resolveExecutionContext(String rawExecutionContext) {
        String normalized = rawExecutionContext.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case RESEARCH, FORWARD_TEST, PAPER -> TEST;
            case LIVE -> LIVE;
            default -> throw new IllegalArgumentException(
                "Unsupported execution context '" + rawExecutionContext
                    + "'. Expected 'research', 'forward-test', 'paper', or 'live'."
            );
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
