package com.algotrader.bot.risk;

import com.algotrader.bot.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the complete risk management workflow.
 * Tests the interaction between PositionSizer, SlippageCalculator, and RiskManager.
 * 
 * Validates end-to-end flow:
 * 1. Position sizing based on 2% risk rule
 * 2. Transaction cost calculation (fees + slippage)
 * 3. Risk manager approval checks
 */
@DisplayName("Risk Management Integration Tests")
class RiskManagementIntegrationTest {

    private PositionSizer positionSizer;
    private SlippageCalculator slippageCalculator;
    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        positionSizer = new PositionSizer();
        slippageCalculator = new SlippageCalculator();
        riskManager = new RiskManager();
    }

    @Test
    @DisplayName("Complete risk workflow: $100 account, 2% risk, BTC trade")
    void testCompleteRiskWorkflow() {
        // GIVEN: $100 account with 2% risk per trade
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // 2%
        BigDecimal maxDrawdownLimit = new BigDecimal("25.00"); // 25%
        
        Account account = new Account(accountBalance, riskPercentage, maxDrawdownLimit);
        
        // BTC trade parameters
        BigDecimal btcEntryPrice = new BigDecimal("50000.00");
        BigDecimal btcStopLoss = new BigDecimal("49000.00"); // 2% stop loss
        
        // STEP 1: Calculate position size
        PositionSizeResult positionResult = positionSizer.calculatePositionSize(
            accountBalance,
            btcEntryPrice,
            btcStopLoss,
            riskPercentage
        );
        
        // Verify position sizing
        assertTrue(positionResult.isValid(), "Position size should be valid");
        assertEquals(new BigDecimal("2.00"), positionResult.getRiskAmount().setScale(2, RoundingMode.HALF_UP),
            "Risk amount should be $2.00 (2% of $100)");
        
        // Expected position size calculation:
        // Risk amount = $100 * 0.02 = $2.00
        // Stop loss distance = |50000 - 49000| / 50000 = 0.02 (2%)
        // Position size = $2.00 / (50000 * 0.02) = 0.002 BTC
        // Notional value = 0.002 * 50000 = $100.00 (exactly at maximum, so capped)
        BigDecimal expectedNotionalValue = new BigDecimal("100.00");
        assertEquals(0, expectedNotionalValue.compareTo(positionResult.getNotionalValue()),
            "Notional value should be $100.00 (capped at maximum)");
        
        // STEP 2: Calculate transaction costs
        // Calculate position size from capped notional value
        BigDecimal actualPositionSize = positionResult.getNotionalValue().divide(
            btcEntryPrice, 8, RoundingMode.HALF_UP
        );
        
        TransactionCost buyCost = slippageCalculator.calculateRealCost(
            btcEntryPrice,
            actualPositionSize,
            true // buy order
        );
        
        // Verify transaction costs
        assertNotNull(buyCost, "Transaction cost should not be null");
        
        // Expected costs for $100 notional:
        // Fees (0.1%) = $100.00 * 0.001 = $0.10
        // Slippage (0.03%) = $100.00 * 0.0003 = $0.03
        // Net cost = $100.00 + $0.10 + $0.03 = $100.13
        assertTrue(buyCost.getTotalFees().compareTo(BigDecimal.ZERO) > 0,
            "Fees should be positive");
        assertTrue(buyCost.getTotalSlippage().compareTo(BigDecimal.ZERO) > 0,
            "Slippage should be positive");
        
        // Effective price should be higher for buy orders
        assertTrue(buyCost.getEffectivePrice().compareTo(btcEntryPrice) > 0,
            "Effective price should be higher than entry price for buy orders");
        
        // STEP 3: Verify risk manager approves trade
        RiskCheckResult riskCheck = riskManager.canTrade(account, new ArrayList<>());
        
        assertTrue(riskCheck.isCanTrade(), "Risk manager should approve trade");
        assertEquals("All risk checks passed", riskCheck.getReason());
        assertEquals(0, BigDecimal.ZERO.compareTo(account.getCurrentDrawdown()),
            "Drawdown should be 0% for new account");
        
        // STEP 4: Verify transaction costs are reasonable
        // Transaction costs (fees + slippage) should be a small percentage of notional value
        BigDecimal totalTransactionCosts = buyCost.getTotalFees().add(buyCost.getTotalSlippage());
        BigDecimal costPercentage = totalTransactionCosts
            .divide(positionResult.getNotionalValue(), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        assertTrue(costPercentage.compareTo(new BigDecimal("0.20")) < 0,
            "Transaction costs should be less than 0.20% of notional value");
    }

    @Test
    @DisplayName("Risk workflow rejects trade when drawdown limit exceeded")
    void testRiskWorkflowRejectsTradeOnDrawdownLimit() {
        // GIVEN: Account with 30% drawdown (exceeds 25% limit)
        BigDecimal initialBalance = new BigDecimal("100.00");
        BigDecimal currentBalance = new BigDecimal("70.00"); // 30% loss
        BigDecimal riskPercentage = new BigDecimal("0.02");
        BigDecimal maxDrawdownLimit = new BigDecimal("25.00");
        
        Account account = new Account(initialBalance, riskPercentage, maxDrawdownLimit);
        account.setCurrentBalance(currentBalance);
        
        // WHEN: Check if trading is allowed
        RiskCheckResult riskCheck = riskManager.canTrade(account, new ArrayList<>());
        
        // THEN: Trading should be blocked
        assertFalse(riskCheck.isCanTrade(), "Trading should be blocked due to drawdown");
        assertTrue(riskCheck.getReason().contains("Drawdown limit exceeded"),
            "Reason should mention drawdown limit");
        assertTrue(riskCheck.getCurrentDrawdown().compareTo(maxDrawdownLimit) > 0,
            "Current drawdown should exceed limit");
    }

    @Test
    @DisplayName("Risk workflow rejects trade when account is stopped")
    void testRiskWorkflowRejectsTradeWhenAccountStopped() {
        // GIVEN: Account with STOPPED status
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");
        BigDecimal maxDrawdownLimit = new BigDecimal("25.00");
        
        Account account = new Account(accountBalance, riskPercentage, maxDrawdownLimit);
        account.setStatus(Account.AccountStatus.STOPPED);
        
        // WHEN: Check if trading is allowed
        RiskCheckResult riskCheck = riskManager.canTrade(account, new ArrayList<>());
        
        // THEN: Trading should be blocked
        assertFalse(riskCheck.isCanTrade(), "Trading should be blocked when account is stopped");
        assertTrue(riskCheck.getReason().contains("STOPPED"),
            "Reason should mention account status");
    }

    @Test
    @DisplayName("Position sizing enforces minimum position size")
    void testPositionSizingEnforcesMinimumSize() {
        // GIVEN: Small account with tight stop loss
        BigDecimal accountBalance = new BigDecimal("50.00");
        BigDecimal riskPercentage = new BigDecimal("0.02"); // $1 risk
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal stopLoss = new BigDecimal("49500.00"); // 1% stop loss
        
        // WHEN: Calculate position size
        PositionSizeResult result = positionSizer.calculatePositionSize(
            accountBalance,
            entryPrice,
            stopLoss,
            riskPercentage
        );
        
        // THEN: Position should be rejected if below minimum
        // Risk amount = $50 * 0.02 = $1.00
        // Stop loss distance = 0.01 (1%)
        // Position size = $1.00 / (50000 * 0.01) = 0.002 BTC
        // Notional = 0.002 * 50000 = $100.00 (above $5 minimum)
        assertTrue(result.isValid(), "Position should be valid");
        assertTrue(result.getNotionalValue().compareTo(new BigDecimal("5.00")) >= 0,
            "Notional value should be above minimum");
    }

    @Test
    @DisplayName("Transaction costs reduce effective returns")
    void testTransactionCostsReduceReturns() {
        // GIVEN: A profitable trade scenario
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal exitPrice = new BigDecimal("51000.00"); // 2% profit
        BigDecimal quantity = new BigDecimal("0.002"); // 0.002 BTC
        
        // WHEN: Calculate buy and sell costs
        TransactionCost buyCost = slippageCalculator.calculateRealCost(entryPrice, quantity, true);
        TransactionCost sellCost = slippageCalculator.calculateRealCost(exitPrice, quantity, false);
        
        // THEN: Effective prices should reflect costs
        // Buy: effective price higher (50000 * 1.0013 = 50065)
        // Sell: effective price lower (51000 * 0.9987 = 50933.70)
        assertTrue(buyCost.getEffectivePrice().compareTo(entryPrice) > 0,
            "Buy effective price should be higher than quoted price");
        assertTrue(sellCost.getEffectivePrice().compareTo(exitPrice) < 0,
            "Sell effective price should be lower than quoted price");
        
        // Calculate net profit after costs
        BigDecimal buyNetCost = buyCost.getNetCost();
        BigDecimal sellNetRevenue = sellCost.getNetCost();
        BigDecimal netProfit = sellNetRevenue.subtract(buyNetCost);
        
        // Gross profit = (51000 - 50000) * 0.002 = $2.00
        // Net profit should be less due to fees and slippage
        BigDecimal grossProfit = new BigDecimal("2.00");
        assertTrue(netProfit.compareTo(grossProfit) < 0,
            "Net profit should be less than gross profit due to transaction costs");
    }

    @Test
    @DisplayName("Complete workflow validates all components work together")
    void testAllComponentsIntegrateProperly() {
        // GIVEN: Realistic trading scenario
        BigDecimal accountBalance = new BigDecimal("100.00");
        BigDecimal riskPercentage = new BigDecimal("0.02");
        BigDecimal maxDrawdownLimit = new BigDecimal("25.00");
        
        Account account = new Account(accountBalance, riskPercentage, maxDrawdownLimit);
        
        BigDecimal entryPrice = new BigDecimal("50000.00");
        BigDecimal stopLoss = new BigDecimal("49000.00");
        
        // WHEN: Execute complete workflow
        
        // Step 1: Check if trading is allowed
        RiskCheckResult riskCheck = riskManager.canTrade(account, new ArrayList<>());
        assertTrue(riskCheck.isCanTrade(), "Trading should be allowed");
        
        // Step 2: Calculate position size
        PositionSizeResult positionResult = positionSizer.calculatePositionSize(
            accountBalance,
            entryPrice,
            stopLoss,
            riskPercentage
        );
        assertTrue(positionResult.isValid(), "Position size should be valid");
        
        // Step 3: Calculate transaction costs
        // Calculate position size from capped notional value
        BigDecimal actualPositionSize = positionResult.getNotionalValue().divide(
            entryPrice, 8, RoundingMode.HALF_UP
        );
        
        TransactionCost buyCost = slippageCalculator.calculateRealCost(
            entryPrice,
            actualPositionSize,
            true
        );
        assertNotNull(buyCost, "Transaction cost should be calculated");
        
        // Step 4: Verify transaction costs are reasonable
        BigDecimal totalTransactionCosts = buyCost.getTotalFees().add(buyCost.getTotalSlippage());
        assertTrue(totalTransactionCosts.compareTo(BigDecimal.ZERO) > 0,
            "Transaction costs should be positive");
        assertTrue(totalTransactionCosts.compareTo(new BigDecimal("1.00")) < 0,
            "Transaction costs should be less than $1.00 for $100 position");
        
        // THEN: All components should work together seamlessly
        assertAll("Complete workflow validation",
            () -> assertTrue(riskCheck.isCanTrade(), "Risk check passed"),
            () -> assertTrue(positionResult.isValid(), "Position sizing passed"),
            () -> assertNotNull(buyCost, "Transaction cost calculated"),
            () -> assertTrue(totalTransactionCosts.compareTo(BigDecimal.ZERO) > 0, "Transaction costs are positive"),
            () -> assertTrue(totalTransactionCosts.compareTo(new BigDecimal("1.00")) < 0, "Transaction costs are reasonable")
        );
    }
}
