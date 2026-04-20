package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.service.BacktestAlgorithmType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BuyAndHoldBacktestStrategy implements BacktestStrategy {

    private static final int ENTRY_INDEX = 20;
    private static final BacktestStrategyDefinition DEFINITION = new BacktestStrategyDefinition(
        BacktestAlgorithmType.BUY_AND_HOLD,
        "Buy and Hold",
        "Baseline benchmark that enters once and holds through the full backtest window.",
        BacktestStrategySelectionMode.SINGLE_SYMBOL,
        ENTRY_INDEX + 1
    );

    @Override
    public BacktestStrategyDefinition definition() {
        return DEFINITION;
    }

    @Override
    public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
        if (!context.inPosition() && context.currentIndex() == ENTRY_INDEX) {
            return BacktestStrategyDecision.buy(context.primarySymbol(), BigDecimal.ONE, "Benchmark entry");
        }

        if (context.inPosition() && context.currentIndex() == context.candles().size() - 1) {
            return BacktestStrategyDecision.sell("Benchmark exit at end of test window");
        }

        return BacktestStrategyDecision.hold();
    }
}
