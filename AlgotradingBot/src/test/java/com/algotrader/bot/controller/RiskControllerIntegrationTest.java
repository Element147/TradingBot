package com.algotrader.bot.controller;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.RiskAlertRepository;
import com.algotrader.bot.repository.RiskConfigRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RiskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private RiskConfigRepository riskConfigRepository;

    @Autowired
    private RiskAlertRepository riskAlertRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String authToken;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        riskAlertRepository.deleteAll();
        riskConfigRepository.deleteAll();

        Account account = new Account(
            new BigDecimal("1000"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );
        accountRepository.save(account);
    }

    @Test
    void getRiskStatus_returnsMetrics() throws Exception {
        mockMvc.perform(get("/api/risk/status")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentDrawdown").exists())
            .andExpect(jsonPath("$.dailyLoss").exists())
            .andExpect(jsonPath("$.openRiskExposure").exists());
    }

    @Test
    void updateRiskConfig_updatesValues() throws Exception {
        UpdateRiskConfigRequest request = new UpdateRiskConfigRequest(
            new BigDecimal("0.03"),
            new BigDecimal("0.06"),
            new BigDecimal("0.30"),
            4,
            new BigDecimal("0.70")
        );

        mockMvc.perform(put("/api/risk/config")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRiskPerTrade").value(0.03))
            .andExpect(jsonPath("$.maxOpenPositions").value(4));
    }

    @Test
    void overrideCircuitBreaker_requiresCode() throws Exception {
        CircuitBreakerOverrideRequest request = new CircuitBreakerOverrideRequest(
            "WRONG",
            "manual override"
        );

        mockMvc.perform(post("/api/risk/circuit-breaker/override")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void overrideCircuitBreaker_blocksLiveEnvironmentHeader() throws Exception {
        CircuitBreakerOverrideRequest request = new CircuitBreakerOverrideRequest(
            "OVERRIDE_PAPER_ONLY",
            "manual override"
        );

        mockMvc.perform(post("/api/risk/circuit-breaker/override")
                .header("Authorization", "Bearer " + authToken)
                .header("X-Environment", "live")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.message").value("Circuit breaker override is disabled for live environment"));
    }

    @Test
    void getAlerts_returnsList() throws Exception {
        mockMvc.perform(get("/api/risk/alerts")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))));
    }
}
