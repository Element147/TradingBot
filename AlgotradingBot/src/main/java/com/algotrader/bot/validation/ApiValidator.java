package com.algotrader.bot.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public class ApiValidator {
    private static final Logger logger = LoggerFactory.getLogger(ApiValidator.class);
    private static final String BASE_URL = "http://localhost:8080";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ValidationHttpClient HTTP = new ValidationHttpClient(BASE_URL, REQUEST_TIMEOUT);

    public ValidationResult validateHealthEndpoint() {
        logger.info("Validating health endpoint");
        try {
            ValidationHttpClient.ValidationHttpResponse response = HTTP.get("/actuator/health");
            if (response.hasStatus(200)) {
                return validateHealthResponse(response.body());
            } else {
                logger.error("Health endpoint returned status: {}", response.statusCode());
                return new ValidationResult(
                    "REQ-9.1", 
                    "Health Endpoint", 
                    ValidationStatus.FAILED, 
                    "Health endpoint returned status: " + response.statusCode()
                );
            }
        } catch (Exception e) {
            logger.error("Error calling health endpoint", e);
            return new ValidationResult(
                "REQ-9.1", 
                "Health Endpoint", 
                ValidationStatus.FAILED, 
                "Error calling health endpoint: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateHealthResponse(String response) {
        logger.info("Validating health response");
        if (response.contains("\"status\":\"UP\"") || response.contains("\"status\": \"UP\"")) {
            logger.info("Health response contains status: UP");
            return new ValidationResult(
                "REQ-9.2", 
                "Health Response", 
                ValidationStatus.PASSED, 
                "Health status is UP"
            );
        } else {
            logger.error("Health response does not contain status: UP");
            return new ValidationResult(
                "REQ-9.2", 
                "Health Response", 
                ValidationStatus.FAILED, 
                "Health status is not UP"
            );
        }
    }

    public ValidationResult validateStrategyStatus() {
        logger.info("Validating strategy status endpoint");
        try {
            ValidationHttpClient.ValidationHttpResponse response = HTTP.get("/api/strategy/status");
            if (response.hasStatus(200)) {
                // Validate JSON
                String responseStr = response.body();
                if (responseStr.startsWith("{") && responseStr.endsWith("}")) {
                    logger.info("Strategy status endpoint returned valid JSON");
                    return new ValidationResult(
                        "REQ-9.3", 
                        "Strategy Status", 
                        ValidationStatus.PASSED, 
                        "Strategy status endpoint working"
                    );
                } else {
                    logger.error("Strategy status response is not valid JSON");
                    return new ValidationResult(
                        "REQ-9.3", 
                        "Strategy Status", 
                        ValidationStatus.FAILED, 
                        "Response is not valid JSON"
                    );
                }
            } else {
                logger.error("Strategy status endpoint returned status: {}", response.statusCode());
                return new ValidationResult(
                    "REQ-9.3", 
                    "Strategy Status", 
                    ValidationStatus.FAILED, 
                    "Strategy status returned status: " + response.statusCode()
                );
            }
        } catch (Exception e) {
            logger.error("Error calling strategy status endpoint", e);
            return new ValidationResult(
                "REQ-9.3", 
                "Strategy Status", 
                ValidationStatus.FAILED, 
                "Error calling strategy status: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateStrategyLifecycle() {
        logger.info("Validating strategy lifecycle (start/stop)");
        
        // Test start
        ValidationResult startResult = validateStrategyStart();
        if (startResult.isFailed()) {
            return startResult;
        }

        pause(Duration.ofSeconds(2));
        // Test stop
        return validateStrategyStop();
    }

    public ValidationResult validateStrategyStart() {
        logger.info("Validating strategy start");
        try {
            String jsonInput = "{\"symbol\":\"BTCUSDT\",\"initialBalance\":100.0}";
            ValidationHttpClient.ValidationHttpResponse response = HTTP.postJson("/api/strategy/start", jsonInput);
            if (response.hasStatus(200, 201)) {
                logger.info("Strategy start successful");
                return new ValidationResult(
                    "REQ-9.5", 
                    "Strategy Start", 
                    ValidationStatus.PASSED, 
                    "Strategy started successfully"
                );
            } else {
                logger.error("Strategy start returned status: {}", response.statusCode());
                return new ValidationResult(
                    "REQ-9.5", 
                    "Strategy Start", 
                    ValidationStatus.FAILED, 
                    "Strategy start returned status: " + response.statusCode()
                );
            }
        } catch (Exception e) {
            logger.error("Error starting strategy", e);
            return new ValidationResult(
                "REQ-9.5", 
                "Strategy Start", 
                ValidationStatus.FAILED, 
                "Error starting strategy: " + e.getMessage()
            );
        }
    }

    public ValidationResult validateStrategyStop() {
        logger.info("Validating strategy stop");
        try {
            ValidationHttpClient.ValidationHttpResponse response = HTTP.post("/api/strategy/stop");
            if (response.hasStatus(200)) {
                logger.info("Strategy stop successful");
                return new ValidationResult(
                    "REQ-9.7", 
                    "Strategy Stop", 
                    ValidationStatus.PASSED, 
                    "Strategy stopped successfully"
                );
            } else {
                logger.error("Strategy stop returned status: {}", response.statusCode());
                return new ValidationResult(
                    "REQ-9.7", 
                    "Strategy Stop", 
                    ValidationStatus.FAILED, 
                    "Strategy stop returned status: " + response.statusCode()
                );
            }
        } catch (Exception e) {
            logger.error("Error stopping strategy", e);
            return new ValidationResult(
                "REQ-9.7", 
                "Strategy Stop", 
                ValidationStatus.FAILED, 
                "Error stopping strategy: " + e.getMessage()
            );
        }
    }

    private void pause(Duration duration) {
        LockSupport.parkNanos(duration.toNanos());
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
    }
}
