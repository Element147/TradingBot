package com.algotrader.bot.service;

import com.algotrader.bot.controller.*;
import com.algotrader.bot.entity.Account;
import com.algotrader.bot.entity.Portfolio;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.entity.Trade;
import com.algotrader.bot.repository.AccountRepository;
import com.algotrader.bot.repository.PortfolioRepository;
import com.algotrader.bot.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AccountService.
 * Tests balance calculation, performance metrics, and environment routing.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ExchangeIntegrationService exchangeIntegrationService;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private List<Portfolio> testPortfolios;
    private List<Trade> testTrades;

    @BeforeEach
    void setUp() {
        // Create test account
        testAccount = new Account(
                new BigDecimal("1000.00000000"),
                new BigDecimal("2.00"),
                new BigDecimal("25.00")
        );
        testAccount.setId(1L);
        testAccount.setCurrentBalance(new BigDecimal("800.00000000"));
        testAccount.setTotalPnl(new BigDecimal("50.00000000"));

        // Create test portfolios
        Portfolio portfolio1 = new Portfolio(
                1L,
                "BTC/USDT",
                new BigDecimal("0.01000000"),
                new BigDecimal("40000.00000000"),
                new BigDecimal("42000.00000000")
        );
        portfolio1.setId(1L);

        Portfolio portfolio2 = new Portfolio(
                1L,
                "ETH/USDT",
                new BigDecimal("0.50000000"),
                new BigDecimal("2000.00000000"),
                new BigDecimal("2100.00000000")
        );
        portfolio2.setId(2L);

        testPortfolios = Arrays.asList(portfolio1, portfolio2);

        // Create test trades
        Trade trade1 = createTrade(1L, "BTC/USDT", new BigDecimal("40000"), 
                                   new BigDecimal("41000"), new BigDecimal("0.01"),
                                   new BigDecimal("10.00"));
        Trade trade2 = createTrade(2L, "ETH/USDT", new BigDecimal("2000"), 
                                   new BigDecimal("1950"), new BigDecimal("0.5"),
                                   new BigDecimal("-25.00"));
        Trade trade3 = createTrade(3L, "BTC/USDT", new BigDecimal("41000"), 
                                   new BigDecimal("42000"), new BigDecimal("0.01"),
                                   new BigDecimal("10.00"));

        testTrades = Arrays.asList(trade1, trade2, trade3);
    }

    @Test
    void testGetBalance_TestEnvironment() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(portfolioRepository.findByAccountId(1L)).thenReturn(testPortfolios);

        // Act
        BalanceResponse response = accountService.getBalance("test", 1L);

        // Assert
        assertNotNull(response);
        // Total = available (800) + portfolio1 (420) + portfolio2 (1050) = 2270
        assertEquals("2270.00000000", response.total());
        assertEquals("800.00000000", response.available());
        assertEquals("1470.00000000", response.locked()); // Portfolio value: 420 + 1050
        assertEquals(3, response.assets().size()); // USDT + 2 positions
        assertNotNull(response.lastSync());
    }

    @Test
    void testGetBalance_LiveEnvironmentThrowsCapabilityError() {
        when(exchangeIntegrationService.getLiveAccountReadUnavailableReason())
            .thenReturn("Live account reads are unavailable on this backend.");

        LiveAccountReadUnavailableException exception = assertThrows(
            LiveAccountReadUnavailableException.class,
            () -> accountService.getBalance("live", 1L)
        );

        assertEquals("Live account reads are unavailable on this backend.", exception.getMessage());
    }

    @Test
    void testGetPerformance_MonthTimeframe() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(portfolioRepository.findByAccountId(1L)).thenReturn(testPortfolios);
        when(tradeRepository.findByAccountIdAndEntryTimeAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(testTrades);

        // Act
        PerformanceResponse response = accountService.getPerformance("test", 1L, "month");

        // Assert
        assertNotNull(response);
        // Total P&L: 10 - 25 + 10 = -5
        assertEquals("-5.00000000", response.totalProfitLoss());
        // Win rate: 2 wins out of 3 trades = 66.67%
        assertNotNull(response.winRate());
        assertEquals(3, response.tradeCount());
        assertNotNull(response.cashRatio());
    }

    @Test
    void testGetPerformance_TodayTimeframe() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(portfolioRepository.findByAccountId(1L)).thenReturn(testPortfolios);
        when(tradeRepository.findByAccountIdAndEntryTimeAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(testTrades.get(0))); // Only one trade today

        // Act
        PerformanceResponse response = accountService.getPerformance("test", 1L, "today");

        // Assert
        assertNotNull(response);
        assertEquals("10.00000000", response.totalProfitLoss());
        assertEquals("100.00000000", response.winRate()); // 1 win out of 1 trade
        assertEquals(1, response.tradeCount());
    }

    @Test
    void testGetOpenPositions() {
        // Arrange
        when(portfolioRepository.findByAccountId(1L)).thenReturn(testPortfolios);

        // Act
        List<OpenPositionResponse> positions = accountService.getOpenPositions("test", 1L);

        // Assert
        assertNotNull(positions);
        assertEquals(2, positions.size());
        
        OpenPositionResponse pos1 = positions.get(0);
        assertEquals("BTC/USDT", pos1.symbol());
        assertEquals("40000.00000000", pos1.entryPrice());
        assertEquals("42000.00000000", pos1.currentPrice());
        assertEquals("20.00000000", pos1.unrealizedPnL()); // (42000 - 40000) * 0.01
    }

    @Test
    void testGetOpenPositions_LiveEnvironmentThrowsCapabilityError() {
        when(exchangeIntegrationService.getLiveAccountReadUnavailableReason())
            .thenReturn("Live account reads are unavailable on this backend.");

        LiveAccountReadUnavailableException exception = assertThrows(
            LiveAccountReadUnavailableException.class,
            () -> accountService.getOpenPositions("live", 1L)
        );

        assertEquals("Live account reads are unavailable on this backend.", exception.getMessage());
    }

    @Test
    void testGetRecentTrades() {
        // Arrange
        when(tradeRepository.findByAccountIdAndExitTimeNotNullOrderByExitTimeDesc(1L))
                .thenReturn(testTrades);

        // Act
        List<RecentTradeResponse> trades = accountService.getRecentTrades("test", 1L, 10);

        // Assert
        assertNotNull(trades);
        assertEquals(3, trades.size());
        
        RecentTradeResponse trade1 = trades.get(0);
        assertEquals("BTC/USDT", trade1.symbol());
        assertEquals("40000.00000000", trade1.entryPrice());
        assertEquals("41000.00000000", trade1.exitPrice());
        assertEquals("10.00000000", trade1.profitLoss());
    }

    @Test
    void testGetRecentTrades_WithLimit() {
        // Arrange
        when(tradeRepository.findByAccountIdAndExitTimeNotNullOrderByExitTimeDesc(1L))
                .thenReturn(testTrades);

        // Act
        List<RecentTradeResponse> trades = accountService.getRecentTrades("test", 1L, 2);

        // Assert
        assertNotNull(trades);
        assertEquals(2, trades.size()); // Limited to 2
    }

    @Test
    void testGetOpenPositions_WithShortPosition() {
        Portfolio shortPortfolio = new Portfolio(
                1L,
                "SOL/USDT",
                new BigDecimal("2.00000000"),
                new BigDecimal("100.00000000"),
                new BigDecimal("90.00000000"),
                PositionSide.SHORT
        );
        shortPortfolio.setId(3L);

        when(portfolioRepository.findByAccountId(1L)).thenReturn(List.of(shortPortfolio));

        List<OpenPositionResponse> positions = accountService.getOpenPositions("test", 1L);

        assertEquals(1, positions.size());
        assertEquals("SHORT", positions.get(0).side());
        assertEquals("20.00000000", positions.get(0).unrealizedPnL());
    }

    @Test
    void testGetRecentTrades_MapsShortSide() {
        Trade shortTrade = createTrade(4L, "SOL/USDT", new BigDecimal("100"),
                new BigDecimal("90"), new BigDecimal("2"),
                new BigDecimal("20.00"));
        shortTrade.setPositionSide(PositionSide.SHORT);

        when(tradeRepository.findByAccountIdAndExitTimeNotNullOrderByExitTimeDesc(1L))
                .thenReturn(List.of(shortTrade));

        List<RecentTradeResponse> trades = accountService.getRecentTrades("test", 1L, 10);

        assertEquals(1, trades.size());
        assertEquals("SHORT", trades.get(0).side());
    }

    @Test
    void testGetBalance_AccountNotFound() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            accountService.getBalance("test", 1L);
        });
    }

    @Test
    void testGetPerformance_NoTrades() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(portfolioRepository.findByAccountId(1L)).thenReturn(testPortfolios);
        when(tradeRepository.findByAccountIdAndEntryTimeAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());

        // Act
        PerformanceResponse response = accountService.getPerformance("test", 1L, "month");

        // Assert
        assertNotNull(response);
        assertEquals("0.00000000", response.totalProfitLoss());
        assertEquals("0.00000000", response.winRate());
        assertEquals(0, response.tradeCount());
    }

    private Trade createTrade(Long id, String symbol, BigDecimal entryPrice, 
                             BigDecimal exitPrice, BigDecimal positionSize, BigDecimal pnl) {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setAccountId(1L);
        trade.setSymbol(symbol);
        trade.setSignalType(Trade.SignalType.BUY);
        trade.setPositionSide(PositionSide.LONG);
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
