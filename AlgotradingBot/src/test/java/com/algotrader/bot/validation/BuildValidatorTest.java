package com.algotrader.bot.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class BuildValidatorTest {
    private BuildValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BuildValidator();
    }

    @Test
    void testValidationResultCreation() {
        ValidationResult result = new ValidationResult(
            "REQ-1", 
            "Test Requirement", 
            ValidationStatus.PASSED, 
            "Test message"
        );
        
        assertTrue(result.isPassed());
        assertFalse(result.isFailed());
        assertEquals("REQ-1", result.getRequirementId());
        assertEquals("Test Requirement", result.getRequirementName());
        assertEquals(ValidationStatus.PASSED, result.getStatus());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void testValidationResultMetadata() {
        ValidationResult result = new ValidationResult(
            "REQ-1", 
            "Test", 
            ValidationStatus.PASSED, 
            "Message"
        );
        
        result.addMetadata("key1", "value1");
        result.addMetadata("key2", 123);
        
        assertEquals("value1", result.getMetadata().get("key1"));
        assertEquals(123, result.getMetadata().get("key2"));
    }

    @Test
    void testValidateJarFileNotFound() {
        Path nonExistentPath = Paths.get("nonexistent.jar");
        ValidationResult result = validator.validateJarFile(nonExistentPath);
        
        assertTrue(result.isFailed());
        assertTrue(result.getMessage().contains("not found"));
    }
}
