package com.algotrader.bot.backtest;

import com.algotrader.bot.backtest.strategy.BacktestIndicatorCalculator;
import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.backtest.strategy.BollingerBandsBacktestStrategy;
import com.algotrader.bot.backtest.strategy.BuyAndHoldBacktestStrategy;
import com.algotrader.bot.backtest.strategy.DualMomentumRotationBacktestStrategy;
import com.algotrader.bot.backtest.strategy.ExhaustionReversalFadeBacktestStrategy;
import com.algotrader.bot.backtest.strategy.MultiTimeframeEmaAdxPullbackBacktestStrategy;
import com.algotrader.bot.backtest.strategy.OpeningRangeVwapBreakoutBacktestStrategy;
import com.algotrader.bot.backtest.strategy.RelativeStrengthRotationIntradayEntryFilterBacktestStrategy;
import com.algotrader.bot.backtest.strategy.RegimeFilteredMeanReversionBacktestStrategy;
import com.algotrader.bot.backtest.strategy.BacktestStrategy;
import com.algotrader.bot.backtest.strategy.BacktestStrategyContext;
import com.algotrader.bot.backtest.strategy.BacktestStrategyDecision;
import com.algotrader.bot.backtest.strategy.BacktestStrategyDefinition;
import com.algotrader.bot.backtest.strategy.BacktestStrategySelectionMode;
import com.algotrader.bot.backtest.strategy.SqueezeBreakoutRegimeConfirmationBacktestStrategy;
import com.algotrader.bot.backtest.strategy.SmaCrossoverBacktestStrategy;
import com.algotrader.bot.backtest.strategy.TrendFirstAdaptiveEnsembleBacktestStrategy;
import com.algotrader.bot.backtest.strategy.TrendPullbackContinuationBacktestStrategy;
import com.algotrader.bot.backtest.strategy.VolatilityManagedDonchianBreakoutBacktestStrategy;
import com.algotrader.bot.backtest.strategy.VwapPullbackContinuationBacktestStrategy;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestSimulationEngineTest {

    private BacktestSimulationEngine simulationEngine;

    @BeforeEach
    void setUp() {
        BacktestIndicatorCalculator indicatorCalculator = new BacktestIndicatorCalculator();
        BacktestStrategyRegistry registry = new BacktestStrategyRegistry(List.of(
            new BuyAndHoldBacktestStrategy(),
            new DualMomentumRotationBacktestStrategy(indicatorCalculator),
            new VolatilityManagedDonchianBreakoutBacktestStrategy(indicatorCalculator),
            new TrendPullbackContinuationBacktestStrategy(indicatorCalculator),
            new RegimeFilteredMeanReversionBacktestStrategy(indicatorCalculator),
            new TrendFirstAdaptiveEnsembleBacktestStrategy(indicatorCalculator),
            new SmaCrossoverBacktestStrategy(indicatorCalculator),
            new BollingerBandsBacktestStrategy(indicatorCalculator),
            new OpeningRangeVwapBreakoutBacktestStrategy(indicatorCalculator),
            new VwapPullbackContinuationBacktestStrategy(indicatorCalculator),
            new ExhaustionReversalFadeBacktestStrategy(indicatorCalculator),
            new MultiTimeframeEmaAdxPullbackBacktestStrategy(indicatorCalculator),
            new SqueezeBreakoutRegimeConfirmationBacktestStrategy(indicatorCalculator),
            new RelativeStrengthRotationIntradayEntryFilterBacktestStrategy(indicatorCalculator)
        ));

        simulationEngine = new BacktestSimulationEngine(registry, new BacktestSimulationMetricsCalculator());
    }

    @Test
    void simulate_buyAndHoldProducesSingleTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.BUY_AND_HOLD,
            request(createRisingCandles("BTC/USDT", 25, new BigDecimal("100")), "BTC/USDT")
        );

        assertEquals(1, result.totalTrades());
        assertTrue(result.finalBalance().compareTo(new BigDecimal("1000")) > 0);
        assertTrue(result.equitySeries().size() >= 2);
        assertEquals(1, result.tradeSeries().size());
    }

    @Test
    void simulate_dualMomentumRotatesAcrossDatasetUniverse() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION,
            request(createMomentumUniverseCandles(), "BTC/USDT")
        );

        assertTrue(result.totalTrades() >= 1);
        assertTrue(result.finalBalance().compareTo(new BigDecimal("1000")) > 0);
        assertEquals(result.totalTrades(), result.tradeSeries().size());
    }

    @Test
    void simulate_donchianBreakoutProducesTrendTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
            request(createDonchianBreakoutCandles("BTC/USDT"), "BTC/USDT")
        );

        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void simulate_trendPullbackContinuationProducesTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.TREND_PULLBACK_CONTINUATION,
            request(createTrendPullbackCandles("BTC/USDT"), "BTC/USDT")
        );

        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void simulate_regimeFilteredMeanReversionProducesTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.REGIME_FILTERED_MEAN_REVERSION,
            request(createMeanReversionCandles("BTC/USDT"), "BTC/USDT")
        );

        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void simulate_trendFirstAdaptiveEnsembleProducesTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE,
            request(createEnsembleUniverseCandles(), "BTC/USDT")
        );

        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void simulate_relativeStrengthRotationWithIntradayFilterProducesTrade() {
        BacktestSimulationResult result = simulationEngine.simulate(
            BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER,
            request(createRelativeStrengthRotationCandles(), "SPY")
        );

        assertTrue(result.totalTrades() >= 1);
        assertEquals(result.totalTrades(), result.tradeSeries().size());
    }

    @Test
    void simulate_rejectsInsufficientCandlesForStrategy() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            simulationEngine.simulate(
                BacktestAlgorithmType.SMA_CROSSOVER,
                request(createRisingCandles("BTC/USDT", 20, new BigDecimal("100")), "BTC/USDT")
            ));

        assertEquals("Not enough candles for selected strategy. Need at least 31 rows.", exception.getMessage());
    }

    @Test
    void simulate_supportsShortTrades() {
        BacktestStrategy shortStrategy = new BacktestStrategy() {
            @Override
            public BacktestStrategyDefinition definition() {
                return new BacktestStrategyDefinition(
                    BacktestAlgorithmType.BUY_AND_HOLD,
                    "Short test strategy",
                    "Verifies short entry and cover support in the simulation engine.",
                    BacktestStrategySelectionMode.SINGLE_SYMBOL,
                    3
                );
            }

            @Override
            public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
                if (!context.inPosition() && context.currentIndex() == 2) {
                    return BacktestStrategyDecision.shortSell(context.primarySymbol(), BigDecimal.ONE, "Enter short");
                }
                if (context.inShortPosition() && context.currentIndex() == context.candles().size() - 1) {
                    return BacktestStrategyDecision.cover("Cover short");
                }
                return BacktestStrategyDecision.hold();
            }
        };

        BacktestSimulationEngine shortEngine = new BacktestSimulationEngine(
            new BacktestStrategyRegistry(List.of(shortStrategy)),
            new BacktestSimulationMetricsCalculator()
        );

        BacktestSimulationResult result = shortEngine.simulate(
            BacktestAlgorithmType.BUY_AND_HOLD,
            request(createFallingCandles("BTC/USDT", 10, new BigDecimal("100")), "BTC/USDT")
        );

        assertEquals(1, result.tradeSeries().size());
        assertEquals(PositionSide.SHORT, result.tradeSeries().get(0).side());
        assertTrue(result.finalBalance().compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void simulate_executesQueuedSignalsOnNextBarOpen() {
        BacktestStrategy nextOpenStrategy = new BacktestStrategy() {
            @Override
            public BacktestStrategyDefinition definition() {
                return new BacktestStrategyDefinition(
                    BacktestAlgorithmType.BUY_AND_HOLD,
                    "Next-open execution test",
                    "Verifies that signal decisions are queued for the next bar open.",
                    BacktestStrategySelectionMode.SINGLE_SYMBOL,
                    3
                );
            }

            @Override
            public BacktestStrategyDecision evaluate(BacktestStrategyContext context) {
                if (!context.inPosition() && context.currentIndex() == 2) {
                    return BacktestStrategyDecision.buy(context.primarySymbol(), BigDecimal.ONE, "Queue long entry");
                }
                if (context.inLongPosition() && context.currentIndex() == 4) {
                    return BacktestStrategyDecision.sell("Queue long exit");
                }
                return BacktestStrategyDecision.hold();
            }
        };

        BacktestSimulationEngine nextOpenEngine = new BacktestSimulationEngine(
            new BacktestStrategyRegistry(List.of(nextOpenStrategy)),
            new BacktestSimulationMetricsCalculator()
        );

        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        List<OHLCVData> candles = List.of(
            candle(start, "BTC/USDT", bd(100), bd(100)),
            candle(start.plusHours(1), "BTC/USDT", bd(101), bd(101)),
            candle(start.plusHours(2), "BTC/USDT", bd(102), bd(105)),
            candle(start.plusHours(3), "BTC/USDT", bd(120), bd(121)),
            candle(start.plusHours(4), "BTC/USDT", bd(122), bd(123)),
            candle(start.plusHours(5), "BTC/USDT", bd(90), bd(89))
        );

        BacktestSimulationResult result = nextOpenEngine.simulate(
            BacktestAlgorithmType.BUY_AND_HOLD,
            request(candles, "BTC/USDT", 0, 0)
        );

        assertEquals(1, result.tradeSeries().size());
        assertEquals(start.plusHours(3), result.tradeSeries().get(0).entryTime());
        assertEquals(bd(120).setScale(8), result.tradeSeries().get(0).entryPrice());
        assertEquals(start.plusHours(5), result.tradeSeries().get(0).exitTime());
        assertEquals(bd(90).setScale(8), result.tradeSeries().get(0).exitPrice());
    }

    private BacktestSimulationRequest request(List<OHLCVData> candles, String primarySymbol) {
        return request(candles, primarySymbol, 10, 3);
    }

    private BacktestSimulationRequest request(List<OHLCVData> candles,
                                              String primarySymbol,
                                              Integer feesBps,
                                              Integer slippageBps) {
        return new BacktestSimulationRequest(
            candles,
            primarySymbol,
            "1h",
            new BigDecimal("1000"),
            feesBps,
            slippageBps
        );
    }

    private List<OHLCVData> createRisingCandles(String symbol, int count, BigDecimal startPrice) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < count; i++) {
            BigDecimal close = startPrice.add(BigDecimal.valueOf(i));
            candles.add(candle(start.plusHours(i), symbol, close));
        }

        return candles;
    }

    private List<OHLCVData> createFallingCandles(String symbol, int count, BigDecimal startPrice) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < count; i++) {
            BigDecimal close = startPrice.subtract(BigDecimal.valueOf(i));
            candles.add(candle(start.plusHours(i), symbol, close));
        }

        return candles;
    }

    private List<OHLCVData> createMomentumUniverseCandles() {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < 240; i++) {
            candles.add(candle(start.plusHours(i), "BTC/USDT", bd(100 + i * 1.2)));
            candles.add(candle(start.plusHours(i), "ETH/USDT", bd(100 + i * 0.6)));
        }

        return candles;
    }

    private List<OHLCVData> createDonchianBreakoutCandles(String symbol) {
        List<OHLCVData> candles = createRisingCandles(symbol, 200, bd(100));
        LocalDateTime start = candles.get(candles.size() - 1).getTimestamp().plusHours(1);

        for (int i = 0; i < 30; i++) {
            candles.add(candle(start.plusHours(i), symbol, bd(310 + i * 0.5)));
        }
        for (int i = 0; i < 15; i++) {
            candles.add(candle(start.plusHours(30 + i), symbol, bd(330 + i * 2)));
        }
        for (int i = 0; i < 25; i++) {
            candles.add(candle(start.plusHours(45 + i), symbol, bd(360 - i * 5)));
        }

        return candles;
    }

    private List<OHLCVData> createTrendPullbackCandles(String symbol) {
        List<OHLCVData> candles = createRisingCandles(symbol, 205, bd(100));
        LocalDateTime start = candles.get(candles.size() - 1).getTimestamp().plusHours(1);

        List<BigDecimal> closes = List.of(
            bd(295), bd(288), bd(280), bd(272), bd(265), bd(270), bd(278), bd(286), bd(294), bd(305),
            bd(314), bd(322), bd(330), bd(336), bd(340), bd(332), bd(320), bd(308), bd(294), bd(280)
        );

        for (int i = 0; i < closes.size(); i++) {
            candles.add(candle(start.plusHours(i), symbol, closes.get(i)));
        }

        return candles;
    }

    private List<OHLCVData> createMeanReversionCandles(String symbol) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < 210; i++) {
            double oscillation = switch (i % 4) {
                case 0 -> 0;
                case 1 -> 1.2;
                case 2 -> -1.1;
                default -> 0.3;
            };
            candles.add(candle(start.plusHours(i), symbol, bd(100 + oscillation)));
        }

        List<BigDecimal> closes = List.of(bd(94), bd(98), bd(101), bd(102), bd(101), bd(100));
        for (int i = 0; i < closes.size(); i++) {
            candles.add(candle(start.plusHours(210 + i), symbol, closes.get(i)));
        }

        return candles;
    }

    private List<OHLCVData> createEnsembleUniverseCandles() {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < 240; i++) {
            candles.add(candle(start.plusHours(i), "BTC/USDT", bd(100 + i * 1.4)));
            candles.add(candle(start.plusHours(i), "ETH/USDT", bd(120 + i * 0.4)));
            candles.add(candle(start.plusHours(i), "SOL/USDT", bd(80 + Math.max(0, i - 180) * 2.2)));
        }

        return candles;
    }

    private List<OHLCVData> createRelativeStrengthRotationCandles() {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int index = 0; index < 260; index++) {
            candles.add(candle(start.plusHours(index), "SPY", closeForSpy(index)));
            candles.add(candle(start.plusHours(index), "QQQ", closeForQqq(index)));
            candles.add(candle(start.plusHours(index), "VTI", closeForVti(index)));
        }

        return candles;
    }

    private OHLCVData candle(LocalDateTime timestamp, String symbol, BigDecimal close) {
        return candle(timestamp, symbol, close, close);
    }

    private OHLCVData candle(LocalDateTime timestamp, String symbol, BigDecimal open, BigDecimal close) {
        return new OHLCVData(
            timestamp,
            symbol,
            open,
            open.max(close).add(BigDecimal.ONE),
            open.min(close).subtract(BigDecimal.ONE),
            close,
            bd(1000)
        );
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private BigDecimal closeForSpy(int index) {
        if (index < 230) {
            return bd(100 + index * 0.35);
        }
        return switch (index) {
            case 230 -> bd(180);
            case 231 -> bd(178);
            case 232 -> bd(176);
            case 233 -> bd(174);
            case 234 -> bd(173);
            case 235 -> bd(175);
            case 236 -> bd(179);
            case 237 -> bd(183);
            default -> bd(183 + (index - 237) * 0.55);
        };
    }

    private BigDecimal closeForQqq(int index) {
        if (index < 245) {
            return bd(100 + index * 0.22);
        }
        return bd(154 + (index - 245) * 1.2);
    }

    private BigDecimal closeForVti(int index) {
        return bd(100 + index * 0.18);
    }
}
