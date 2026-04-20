package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiValidatorTest {
    private ApiValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ApiValidator();
    }

    @Test
    void testValidateHealthResponse() {
        String validResponse = "{\"status\":\"UP\"}";
        ValidationResult result = validator.validateHealthResponse(validResponse);
        assertTrue(result.isPassed());
    }

    @Test
    void testValidateHealthResponseWithSpaces() {
        String validResponse = "{\"status\": \"UP\"}";
        ValidationResult result = validator.validateHealthResponse(validResponse);
        assertTrue(result.isPassed());
    }

    @Test
    void testValidateHealthResponseDown() {
        String invalidResponse = "{\"status\":\"DOWN\"}";
        ValidationResult result = validator.validateHealthResponse(invalidResponse);
        assertTrue(result.isFailed());
    }
}
