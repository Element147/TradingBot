package com.algotrader.bot.controller;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.StrategyConfig;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.StrategyConfigRepository;
import com.algotrader.bot.repository.StrategyConfigVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "algotrading.security.relaxed-auth=true")
@Transactional
class RelaxedAuthFastDevIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StrategyConfigRepository strategyConfigRepository;

    @Autowired
    private StrategyConfigVersionRepository strategyConfigVersionRepository;

    @BeforeEach
    void setUp() {
        strategyConfigVersionRepository.deleteAll();
        strategyConfigRepository.deleteAll();

        accountRepository.save(new Account(
                new BigDecimal("1000.00000000"),
                new BigDecimal("2.00"),
                new BigDecimal("25.00")
        ));

        strategyConfigRepository.save(new StrategyConfig(
                "Fast Dev Strategy",
                "bollinger-bands",
                "BTC/USDT",
                "1h",
                new BigDecimal("0.02"),
                new BigDecimal("10.00"),
                new BigDecimal("100.00")
        ));
    }

    @Test
    void protectedAccountEndpoint_allowsUnauthenticatedAccess_whenRelaxedAuthEnabled() throws Exception {
        mockMvc.perform(get("/api/account/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").exists());
    }

    @Test
    void protectedStrategiesEndpoint_allowsUnauthenticatedAccess_whenRelaxedAuthEnabled() throws Exception {
        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}
