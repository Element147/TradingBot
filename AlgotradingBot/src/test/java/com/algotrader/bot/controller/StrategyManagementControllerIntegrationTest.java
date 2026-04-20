package com.algotrader.bot.controller;

import com.algotrader.bot.entity.StrategyConfig;
import com.algotrader.bot.repository.StrategyConfigRepository;
import com.algotrader.bot.repository.StrategyConfigVersionRepository;
import com.algotrader.bot.security.JwtTokenProvider;
import com.algotrader.bot.service.BacktestAlgorithmType;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StrategyManagementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrategyConfigRepository strategyConfigRepository;

    @Autowired
    private StrategyConfigVersionRepository strategyConfigVersionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String authToken;
    private Long strategyId;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        strategyConfigRepository.deleteAll();
        strategyConfigVersionRepository.deleteAll();

        StrategyConfig strategy = new StrategyConfig(
            "Test Strategy",
            "bollinger-bands",
            "BTC/USDT",
            "1h",
            new BigDecimal("0.02"),
            new BigDecimal("10.00"),
            new BigDecimal("100.00")
        );
        strategyId = strategyConfigRepository.save(strategy).getId();
    }

    @Test
    void listStrategies_returnsStrategies() throws Exception {
        mockMvc.perform(get("/api/strategies")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(BacktestAlgorithmType.values().length))))
            .andExpect(jsonPath("$[*].type", hasItem("BOLLINGER_BANDS")))
            .andExpect(jsonPath("$[?(@.name=='Test Strategy')].type", hasItem("BOLLINGER_BANDS")))
            .andExpect(jsonPath("$[?(@.name=='Test Strategy')].configVersion", hasItem(1)))
            .andExpect(jsonPath("$[?(@.name=='Test Strategy')].shortSellingEnabled", hasItem(false)))
            .andExpect(jsonPath("$[?(@.name=='Test Strategy')].lastConfigChangedAt").exists());
    }

    @Test
    void startStrategy_updatesStatus() throws Exception {
        mockMvc.perform(post("/api/strategies/{strategyId}/start", strategyId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value(strategyId))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void stopStrategy_updatesStatus() throws Exception {
        mockMvc.perform(post("/api/strategies/{strategyId}/stop", strategyId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.strategyId").value(strategyId))
            .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void updateConfig_validatesRangeAndUpdates() throws Exception {
        UpdateStrategyConfigRequest request = new UpdateStrategyConfigRequest(
            "ETH/USDT",
            "4h",
            new BigDecimal("0.03"),
            new BigDecimal("20.00"),
            new BigDecimal("120.00")
        );

        mockMvc.perform(put("/api/strategies/{strategyId}/config", strategyId)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("ETH/USDT"))
            .andExpect(jsonPath("$.timeframe").value("4h"))
            .andExpect(jsonPath("$.riskPerTrade").value(0.03))
            .andExpect(jsonPath("$.configVersion").value(2));
    }

    @Test
    void updateConfig_rejectsInvalidRisk() throws Exception {
        UpdateStrategyConfigRequest request = new UpdateStrategyConfigRequest(
            "ETH/USDT",
            "4h",
            new BigDecimal("0.10"),
            new BigDecimal("20.00"),
            new BigDecimal("120.00")
        );

        mockMvc.perform(put("/api/strategies/{strategyId}/config", strategyId)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void configHistory_returnsInitialAndUpdatedVersions() throws Exception {
        UpdateStrategyConfigRequest request = new UpdateStrategyConfigRequest(
            "ETH/USDT",
            "4h",
            new BigDecimal("0.03"),
            new BigDecimal("20.00"),
            new BigDecimal("120.00")
        );

        mockMvc.perform(put("/api/strategies/{strategyId}/config", strategyId)
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/strategies/{strategyId}/config-history", strategyId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].versionNumber").value(2))
            .andExpect(jsonPath("$[0].changeReason").value("Updated symbol, timeframe, riskPerTrade, minPositionSize, maxPositionSize"))
            .andExpect(jsonPath("$[0].symbol").value("ETH/USDT"))
            .andExpect(jsonPath("$[1].versionNumber").value(1))
            .andExpect(jsonPath("$[1].changeReason").exists());
    }
}
