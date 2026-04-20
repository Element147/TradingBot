package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrchestrationValidatorTest {
    private OrchestrationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrchestrationValidator();
    }

    @Test
    void testValidatorCreation() {
        assertNotNull(validator);
    }
}
