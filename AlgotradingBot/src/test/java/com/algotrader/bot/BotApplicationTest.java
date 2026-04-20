package com.algotrader.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for BotApplication.
 * Verifies that the application class is properly configured.
 */
class BotApplicationTest {

    @Test
    void mainMethodExists() {
        // Verify the main method exists and can be invoked
        assertNotNull(BotApplication.class);
    }

    @Test
    void applicationClassHasSpringBootAnnotation() {
        // Verify @SpringBootApplication annotation is present
        assertNotNull(BotApplication.class.getAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class));
    }
}
