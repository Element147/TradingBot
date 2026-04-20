package com.algotrader.bot.controller;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
import com.algotrader.bot.security.JwtTokenProvider;
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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AccountController.
 * Tests REST endpoints with actual database and security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Generate auth token
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");

        // Create test account
        testAccount = new Account(
                new BigDecimal("1000.00000000"),
                new BigDecimal("2.00"),
                new BigDecimal("25.00")
        );
        testAccount.setCurrentBalance(new BigDecimal("900.00000000"));
        testAccount.setTotalPnl(new BigDecimal("50.00000000"));
        testAccount = accountRepository.save(testAccount);

        // Create test portfolios
        Portfolio portfolio1 = new Portfolio(
                testAccount.getId(),
                "BTC/USDT",
                new BigDecimal("0.01000000"),
                new BigDecimal("40000.00000000"),
                new BigDecimal("42000.00000000")
        );
        portfolioRepository.save(portfolio1);

        Portfolio portfolio2 = new Portfolio(
                testAccount.getId(),
                "ETH/USDT",
                new BigDecimal("0.50000000"),
                new BigDecimal("2000.00000000"),
                new BigDecimal("2100.00000000")
        );
        portfolioRepository.save(portfolio2);

        // Create test trades
        Trade trade1 = createTrade("BTC/USDT", new BigDecimal("40000"), 
                                   new BigDecimal("41000"), new BigDecimal("0.01"),
                                   new BigDecimal("10.00"));
        tradeRepository.save(trade1);

        Trade trade2 = createTrade("ETH/USDT", new BigDecimal("2000"), 
                                   new BigDecimal("1950"), new BigDecimal("0.5"),
                                   new BigDecimal("-25.00"));
        tradeRepository.save(trade2);
    }

    @Test
    void testGetBalance_Success() throws Exception {
        mockMvc.perform(get("/api/account/balance")
                        .param("env", "test")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.available").value("900.00000000"))
                .andExpect(jsonPath("$.locked").exists())
                .andExpect(jsonPath("$.assets").isArray())
                .andExpect(jsonPath("$.assets", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.lastSync").exists());
    }

    @Test
    void testGetBalance_DefaultEnvironment() throws Exception {
        mockMvc.perform(get("/api/account/balance")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.available").exists());
    }

    @Test
    void testGetBalance_UsesEnvironmentHeaderRouting() throws Exception {
        mockMvc.perform(get("/api/account/balance")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Environment", "live")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Live account reads are unavailable")));
    }

    @Test
    void testGetBalance_QueryEnvironmentOverridesHeader() throws Exception {
        mockMvc.perform(get("/api/account/balance")
                        .param("env", "test")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Environment", "live")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value("900.00000000"));
    }

    @Test
    void testGetBalance_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/account/balance")
                        .param("env", "test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetPerformance_Success() throws Exception {
        mockMvc.perform(get("/api/account/performance")
                        .param("env", "test")
                        .param("timeframe", "month")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProfitLoss").exists())
                .andExpect(jsonPath("$.profitLossPercentage").exists())
                .andExpect(jsonPath("$.winRate").exists())
                .andExpect(jsonPath("$.tradeCount").isNumber())
                .andExpect(jsonPath("$.cashRatio").exists());
    }

    @Test
    void testGetPerformance_TodayTimeframe() throws Exception {
        mockMvc.perform(get("/api/account/performance")
                        .param("env", "test")
                        .param("timeframe", "today")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").isNumber());
    }

    @Test
    void testGetPerformance_AllTimeTimeframe() throws Exception {
        mockMvc.perform(get("/api/account/performance")
                        .param("env", "test")
                        .param("timeframe", "all-time")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void testGetOpenPositions_Success() throws Exception {
        mockMvc.perform(get("/api/positions/open")
                        .param("env", "test")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].entryPrice").exists())
                .andExpect(jsonPath("$[0].currentPrice").exists())
                .andExpect(jsonPath("$[0].unrealizedPnL").exists());
    }

    @Test
    void testGetRecentTrades_Success() throws Exception {
        mockMvc.perform(get("/api/trades/recent")
                        .param("env", "test")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))))
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].entryPrice").exists())
                .andExpect(jsonPath("$[0].exitPrice").exists())
                .andExpect(jsonPath("$[0].profitLoss").exists());
    }

    @Test
    void testGetRecentTrades_WithCustomLimit() throws Exception {
        mockMvc.perform(get("/api/trades/recent")
                        .param("env", "test")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetRecentTrades_DefaultLimit() throws Exception {
        mockMvc.perform(get("/api/trades/recent")
                        .param("env", "test")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testGetRecentTrades_LiveEnvironmentReturnsCapabilityError() throws Exception {
        mockMvc.perform(get("/api/trades/recent")
                        .header("Authorization", "Bearer " + authToken)
                        .header("X-Environment", "live")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Live account reads are unavailable")));
    }

    private Trade createTrade(String symbol, BigDecimal entryPrice, 
                             BigDecimal exitPrice, BigDecimal positionSize, BigDecimal pnl) {
        Trade trade = new Trade();
        trade.setAccountId(testAccount.getId());
        trade.setSymbol(symbol);
        trade.setSignalType(Trade.SignalType.BUY);
        trade.setEntryPrice(entryPrice);
        trade.setExitPrice(exitPrice);
        trade.setPositionSize(positionSize);
        trade.setPnl(pnl);
        trade.setEntryTime(LocalDateTime.now().minusDays(1));
        trade.setExitTime(LocalDateTime.now());
        trade.setStopLoss(entryPrice.multiply(new BigDecimal("0.98")));
        trade.setTakeProfit(entryPrice.multiply(new BigDecimal("1.02")));
        trade.setRiskAmount(new BigDecimal("20.00"));
        trade.setActualFees(new BigDecimal("0.40"));
        trade.setActualSlippage(new BigDecimal("0.12"));
        return trade;
    }
}
