package com.algotrader.bot.controller;

import com.algotrader.bot.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GlobalExceptionHandler.
 * Verifies that all exception types are handled correctly with proper HTTP status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
    }

    @Test
    void testValidationError_Returns400BadRequest() throws Exception {
        // Create invalid request (negative initial balance)
        StartStrategyRequest invalidRequest = new StartStrategyRequest(
            new BigDecimal("-100.00"),  // Invalid: negative balance
            List.of("BTC/USDT"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/strategy/start"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testValidationError_EmptyPairs_Returns400BadRequest() throws Exception {
        // Create invalid request (empty pairs list)
        StartStrategyRequest invalidRequest = new StartStrategyRequest(
            new BigDecimal("100.00"),
            List.of(),  // Invalid: empty pairs
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testNotFoundError_Returns404NotFound() throws Exception {
        // Try to stop strategy with non-existent account ID
        mockMvc.perform(post("/api/strategy/stop")
                .param("accountId", "999999")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/strategy/stop"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testMissingApiRoute_Returns404Not500() throws Exception {
        mockMvc.perform(get("/api/missing/route")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
