package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataPersistenceValidatorTest {
    private DataPersistenceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DataPersistenceValidator();
    }

    @Test
    void testValidatorCreation() {
        assertNotNull(validator);
    }
}
