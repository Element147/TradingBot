package com.algotrader.bot.controller;

import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.BacktestResult;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.BacktestResultRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
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
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for TradingStrategyController.
 * Tests all endpoints including start, stop, status, trade history, and backtest results.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TradingStrategyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private BacktestResultRepository backtestResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String authToken;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        authToken = jwtTokenProvider.generateToken("testuser", "ROLE_USER");

        // Clean up
        tradeRepository.deleteAll();
        portfolioRepository.deleteAll();
        accountRepository.deleteAll();
        backtestResultRepository.deleteAll();

        // Create test account
        testAccount = new Account(
            new BigDecimal("1000.00"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );
        testAccount.setCurrentBalance(new BigDecimal("1100.00"));
        testAccount.setTotalPnl(new BigDecimal("100.00"));
        testAccount = accountRepository.save(testAccount);

        // Create test portfolio positions
        Portfolio btcPosition = new Portfolio(
            testAccount.getId(),
            "BTC/USDT",
            new BigDecimal("0.01"),
            new BigDecimal("50000.00"),
            new BigDecimal("52000.00")
        );
        portfolioRepository.save(btcPosition);

        Portfolio ethPosition = new Portfolio(
            testAccount.getId(),
            "ETH/USDT",
            new BigDecimal("0.5"),
            new BigDecimal("3000.00"),
            new BigDecimal("3100.00")
        );
        portfolioRepository.save(ethPosition);
    }

    // ========== START STRATEGY TESTS ==========

    @Test
    void testStartStrategy_ValidRequest_ReturnsSuccess() throws Exception {
        // Prepare request
        StartStrategyRequest request = new StartStrategyRequest(
            new BigDecimal("500.00"),
            java.util.Arrays.asList("BTC/USDT", "ETH/USDT"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Trading strategy started successfully"))
                .andExpect(jsonPath("$.initialBalance").value(500.00));
    }

    @Test
    void testStartStrategy_InvalidRequest_NegativeBalance_ReturnsBadRequest() throws Exception {
        // Prepare invalid request with negative balance
        String invalidRequestJson = """
            {
                "initialBalance": -100.00,
                "pairs": ["BTC/USDT"],
                "riskPerTrade": 0.02,
                "maxDrawdown": 0.25
            }
            """;

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testStartStrategy_InvalidRequest_EmptyPairs_ReturnsBadRequest() throws Exception {
        // Prepare invalid request with empty pairs
        String invalidRequestJson = """
            {
                "initialBalance": 100.00,
                "pairs": [],
                "riskPerTrade": 0.02,
                "maxDrawdown": 0.25
            }
            """;

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(invalidRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testStartStrategy_InvalidRequest_ExcessiveRisk_ReturnsBadRequest() throws Exception {
        // Prepare invalid request with risk > 5%
        String invalidRequestJson = """
            {
                "initialBalance": 100.00,
                "pairs": ["BTC/USDT"],
                "riskPerTrade": 0.10,
                "maxDrawdown": 0.25
            }
            """;

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(invalidRequestJson))
                .andExpect(status().isBadRequest());
    }

    // ========== STATUS TESTS ==========

    @Test
    void testGetStatus_ExistingAccount_ReturnsSuccess() throws Exception {
        mockMvc.perform(get("/api/strategy/status")
                .param("accountId", testAccount.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountValue").exists())
                .andExpect(jsonPath("$.pnl").value(100.00))
                .andExpect(jsonPath("$.pnlPercent").value(10.00))
                .andExpect(jsonPath("$.openPositions").value(2))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testGetStatus_UsesOnlyTradesForRequestedAccount() throws Exception {
        Account anotherAccount = new Account(
            new BigDecimal("1000.00"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );
        anotherAccount = accountRepository.save(anotherAccount);

        Trade trade = new Trade(
            anotherAccount.getId(),
            "BTC/USDT",
            Trade.SignalType.BUY,
            LocalDateTime.now().minusDays(1),
            new BigDecimal("50000.00"),
            new BigDecimal("0.01"),
            new BigDecimal("20.00"),
            new BigDecimal("49000.00"),
            new BigDecimal("51000.00"),
            new BigDecimal("5.00"),
            new BigDecimal("1.50")
        );
        trade.setPnl(new BigDecimal("10.00"));
        tradeRepository.save(trade);

        mockMvc.perform(get("/api/strategy/status")
                .param("accountId", testAccount.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTrades").value(0));
    }

    @Test
    void testGetStatus_WithoutAccountId_UsesLatestAccount() throws Exception {
        mockMvc.perform(get("/api/strategy/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountValue").exists())
                .andExpect(jsonPath("$.pnl").exists())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testGetStatus_NonExistentAccount_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/strategy/status")
                .param("accountId", "99999")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/strategy/status"));
    }

    // ========== STOP STRATEGY TESTS ==========

    @Test
    void testStopStrategy_Success() throws Exception {
        mockMvc.perform(post("/api/strategy/stop")
                .param("accountId", testAccount.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId()))
                .andExpect(jsonPath("$.status").value("STOPPED"))
                .andExpect(jsonPath("$.finalBalance").value(3170.00))
                .andExpect(jsonPath("$.totalPnl").value(170.00))
                .andExpect(jsonPath("$.pnlPercent").value(217.00))
                .andExpect(jsonPath("$.message").value("Trading strategy stopped successfully"));

        // Verify account status updated
        Account updatedAccount = accountRepository.findById(testAccount.getId()).orElseThrow();
        assert updatedAccount.getStatus() == Account.AccountStatus.STOPPED;

        // Verify positions deleted
        var positions = portfolioRepository.findByAccountId(testAccount.getId());
        assert positions.isEmpty() : "All positions should be deleted when strategy stops";
    }

    @Test
    void testStopStrategy_WithoutAccountId_UsesLatest() throws Exception {
        mockMvc.perform(post("/api/strategy/stop")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId()))
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }


    @Test
    void testGetTradeHistory_WithoutFilters_ReturnsAllTrades() throws Exception {
        // Create test trades
        createTestTrades();

        mockMvc.perform(get("/api/trades/history")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[0].pair").exists())
                .andExpect(jsonPath("$[0].entryPrice").exists())
                .andExpect(jsonPath("$[0].positionSize").exists());
    }

    // ========== TRADE HISTORY TESTS ==========


    @Test
    void testGetTradeHistory_WithSymbolFilter_ReturnsFilteredTrades() throws Exception {
        // Create test trades
        createTestTrades();

        mockMvc.perform(get("/api/trades/history")
                .param("symbol", "BTC/USDT")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].pair").value("BTC/USDT"));
    }

    @Test
    void testGetTradeHistory_WithLimit_RespectsLimit() throws Exception {
        // Create test trades
        createTestTrades();

        mockMvc.perform(get("/api/trades/history")
                .param("limit", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(2))));
    }

    @Test
    void testGetTradeHistory_WithAccountFilter_ReturnsOnlyRequestedAccountTrades() throws Exception {
        createTestTrades();

        Account anotherAccount = new Account(
            new BigDecimal("1000.00"),
            new BigDecimal("0.02"),
            new BigDecimal("0.25")
        );
        anotherAccount = accountRepository.save(anotherAccount);

        Trade anotherTrade = new Trade(
            anotherAccount.getId(),
            "SOL/USDT",
            Trade.SignalType.BUY,
            LocalDateTime.now().minusHours(12),
            new BigDecimal("100.00"),
            new BigDecimal("1.0"),
            new BigDecimal("10.00"),
            new BigDecimal("95.00"),
            new BigDecimal("105.00"),
            new BigDecimal("0.10"),
            new BigDecimal("0.03")
        );
        tradeRepository.save(anotherTrade);

        mockMvc.perform(get("/api/trades/history")
                .param("accountId", testAccount.getId().toString())
                .param("symbol", "SOL/USDT")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetTradeDetails_ReturnsRequestedTrade() throws Exception {
        createTestTrades();
        Trade trade = tradeRepository.findAll().stream()
            .filter(existing -> "ETH/USDT".equals(existing.getSymbol()))
            .findFirst()
            .orElseThrow();

        mockMvc.perform(get("/api/trades/{tradeId}", trade.getId())
                .param("accountId", testAccount.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(trade.getId()))
            .andExpect(jsonPath("$.pair").value("ETH/USDT"));
    }

    @Test
    void testGetTradeDetails_WithWrongAccountScope_ReturnsNotFound() throws Exception {
        createTestTrades();
        Trade trade = tradeRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/trades/{tradeId}", trade.getId())
                .param("accountId", "99999")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message", containsString("Trade not found")));
    }

    private void createTestTrades() {
        // Create BTC trade
        Trade btcTrade = new Trade(
            testAccount.getId(),
            "BTC/USDT",
            Trade.SignalType.BUY,
            LocalDateTime.now().minusDays(3),
            new BigDecimal("50000.00"),
            new BigDecimal("0.01"),
            new BigDecimal("20.00"),
            new BigDecimal("49000.00"),
            new BigDecimal("51000.00"),
            new BigDecimal("5.00"),
            new BigDecimal("1.50")
        );
        btcTrade.setExitTime(LocalDateTime.now().minusDays(2));
        btcTrade.setExitPrice(new BigDecimal("51000.00"));
        btcTrade.setPnl(new BigDecimal("10.00"));
        tradeRepository.save(btcTrade);

        // Create ETH trade
        Trade ethTrade = new Trade(
            testAccount.getId(),
            "ETH/USDT",
            Trade.SignalType.BUY,
            LocalDateTime.now().minusDays(2),
            new BigDecimal("3000.00"),
            new BigDecimal("0.5"),
            new BigDecimal("30.00"),
            new BigDecimal("2900.00"),
            new BigDecimal("3100.00"),
            new BigDecimal("1.50"),
            new BigDecimal("0.45")
        );
        ethTrade.setExitTime(LocalDateTime.now().minusDays(1));
        ethTrade.setExitPrice(new BigDecimal("3100.00"));
        ethTrade.setPnl(new BigDecimal("50.00"));
        tradeRepository.save(ethTrade);

        // Create another BTC trade
        Trade btcTrade2 = new Trade(
            testAccount.getId(),
            "BTC/USDT",
            Trade.SignalType.BUY,
            LocalDateTime.now().minusDays(1),
            new BigDecimal("51000.00"),
            new BigDecimal("0.01"),
            new BigDecimal("20.00"),
            new BigDecimal("50000.00"),
            new BigDecimal("52000.00"),
            new BigDecimal("5.10"),
            new BigDecimal("1.53")
        );
        tradeRepository.save(btcTrade2);
    }


    @Test
    void testGetBacktestResults_WithoutFilters_ReturnsAllResults() throws Exception {
        // Create test backtest results
        createTestBacktestResults();

        mockMvc.perform(get("/api/backtest/results")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].strategyId").exists())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].sharpeRatio").exists())
                .andExpect(jsonPath("$[0].profitFactor").exists());
    }

    // ========== BACKTEST RESULTS TESTS ==========


    @Test
    void testGetBacktestResults_WithStrategyIdFilter_ReturnsFilteredResults() throws Exception {
        // Create test backtest results
        createTestBacktestResults();

        mockMvc.perform(get("/api/backtest/results")
                .param("strategyId", "bollinger-bands-v1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].strategyId").value("bollinger-bands-v1"));
    }

    @Test
    void testGetBacktestResults_WithSymbolFilter_ReturnsFilteredResults() throws Exception {
        // Create test backtest results
        createTestBacktestResults();

        mockMvc.perform(get("/api/backtest/results")
                .param("symbol", "BTC/USDT")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].symbol").value("BTC/USDT"));
    }

    @Test
    void testGetBacktestResults_WithLimit_RespectsLimit() throws Exception {
        // Create test backtest results
        createTestBacktestResults();

        mockMvc.perform(get("/api/backtest/results")
                .param("limit", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private void createTestBacktestResults() {
        // Create BTC backtest result
        com.algotrader.bot.entity.BacktestResult btcResult = new com.algotrader.bot.entity.BacktestResult(
            "bollinger-bands-v1",
            "BTC/USDT",
            LocalDateTime.now().minusYears(2),
            LocalDateTime.now(),
            new BigDecimal("1000.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("1.25"),
            new BigDecimal("1.8"),
            new BigDecimal("52.5"),
            new BigDecimal("18.5"),
            150,
            com.algotrader.bot.entity.BacktestResult.ValidationStatus.PASSED
        );
        backtestResultRepository.save(btcResult);

        // Create ETH backtest result
        com.algotrader.bot.entity.BacktestResult ethResult = new com.algotrader.bot.entity.BacktestResult(
            "bollinger-bands-v1",
            "ETH/USDT",
            LocalDateTime.now().minusYears(2),
            LocalDateTime.now(),
            new BigDecimal("1000.00"),
            new BigDecimal("1350.00"),
            new BigDecimal("1.15"),
            new BigDecimal("1.6"),
            new BigDecimal("48.0"),
            new BigDecimal("22.0"),
            120,
            com.algotrader.bot.entity.BacktestResult.ValidationStatus.PASSED
        );
        backtestResultRepository.save(ethResult);

        // Create another strategy result
        com.algotrader.bot.entity.BacktestResult anotherResult = new com.algotrader.bot.entity.BacktestResult(
            "ema-crossover-v1",
            "BTC/USDT",
            LocalDateTime.now().minusYears(1),
            LocalDateTime.now(),
            new BigDecimal("1000.00"),
            new BigDecimal("1200.00"),
            new BigDecimal("0.95"),
            new BigDecimal("1.4"),
            new BigDecimal("45.0"),
            new BigDecimal("28.0"),
            80,
            com.algotrader.bot.entity.BacktestResult.ValidationStatus.FAILED
        );
        backtestResultRepository.save(anotherResult);
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    void testErrorHandling_MalformedJson_ReturnsBadRequest() throws Exception {
        String malformedJson = "{ invalid json }";

        // Malformed JSON is normalized by GlobalExceptionHandler to 400 with consistent error payload.
        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void testErrorHandling_MissingRequiredFields_ReturnsBadRequest() throws Exception {
        String incompleteJson = """
            {
                "pairs": ["BTC/USDT"]
            }
            """;

        mockMvc.perform(post("/api/strategy/start")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken)
                .content(incompleteJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testErrorHandling_ConsistentErrorFormat() throws Exception {
        // Test that all error responses follow the same format
        mockMvc.perform(get("/api/strategy/status")
                .param("accountId", "99999")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists());
    }

}
