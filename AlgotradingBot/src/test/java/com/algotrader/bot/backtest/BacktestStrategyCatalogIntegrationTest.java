package com.algotrader.bot.backtest;

import com.algotrader.bot.backtest.strategy.BacktestStrategyRegistry;
import com.algotrader.bot.entity.PositionSide;
import com.algotrader.bot.service.BacktestAlgorithmType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BacktestStrategyCatalogIntegrationTest {

    @Autowired
    private BacktestSimulationEngine simulationEngine;

    @Autowired
    private BacktestStrategyRegistry strategyRegistry;

    @Test
    void buyAndHold_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.BUY_AND_HOLD,
            createRisingCandles("BTC/USDT", 30, bd(100)),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.BUY_AND_HOLD));
        assertEquals(1, result.totalTrades());
        assertTrue(result.finalBalance().compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void dualMomentumRotation_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION,
            createMomentumUniverseCandles(),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.DUAL_MOMENTUM_ROTATION));
        assertTrue(result.totalTrades() >= 1);
        assertTrue(result.finalBalance().compareTo(new BigDecimal("1000")) > 0);
    }

    @Test
    void volatilityManagedDonchianBreakout_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT,
            createDonchianBreakoutCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.VOLATILITY_MANAGED_DONCHIAN_BREAKOUT));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void trendPullbackContinuation_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.TREND_PULLBACK_CONTINUATION,
            createTrendPullbackCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.TREND_PULLBACK_CONTINUATION));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void regimeFilteredMeanReversion_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.REGIME_FILTERED_MEAN_REVERSION,
            createMeanReversionCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.REGIME_FILTERED_MEAN_REVERSION));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void trendFirstAdaptiveEnsemble_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE,
            createEnsembleUniverseCandles(),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.TREND_FIRST_ADAPTIVE_ENSEMBLE));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void smaCrossover_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.SMA_CROSSOVER,
            createSmaCrossoverCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.SMA_CROSSOVER));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void bollingerBands_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.BOLLINGER_BANDS,
            createBollingerCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.BOLLINGER_BANDS));
        assertTrue(result.totalTrades() >= 1);
    }

    @Test
    void ichimokuTrend_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.ICHIMOKU_TREND,
            createIchimokuCandles("BTC/USDT"),
            "BTC/USDT"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.ICHIMOKU_TREND));
        assertTrue(result.totalTrades() >= 1);
        assertEquals(PositionSide.LONG, result.tradeSeries().get(0).side());
    }

    @Test
    void relativeStrengthRotationIntradayFilter_strategyRunsEndToEnd() {
        BacktestSimulationResult result = simulate(
            BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER,
            createRelativeStrengthRotationCandles(),
            "SPY"
        );

        assertNotNull(strategyRegistry.getStrategy(BacktestAlgorithmType.RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER));
        assertTrue(result.totalTrades() >= 1);
    }

    private BacktestSimulationResult simulate(BacktestAlgorithmType algorithmType,
                                              List<OHLCVData> candles,
                                              String primarySymbol) {
        return simulationEngine.simulate(
            algorithmType,
            new BacktestSimulationRequest(
                candles,
                primarySymbol,
                "1h",
                new BigDecimal("1000"),
                10,
                3
            )
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

    private List<OHLCVData> createSmaCrossoverCandles(String symbol) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");
        List<BigDecimal> closes = List.of(
            bd(100), bd(99), bd(98), bd(97), bd(96), bd(95), bd(94), bd(93), bd(92), bd(91),
            bd(90), bd(89), bd(88), bd(87), bd(86), bd(85), bd(84), bd(83), bd(82), bd(81),
            bd(80), bd(79), bd(78), bd(77), bd(76), bd(75), bd(74), bd(73), bd(72), bd(71),
            bd(90), bd(95), bd(100), bd(105), bd(110), bd(115), bd(108), bd(100), bd(92), bd(84),
            bd(76), bd(68), bd(60), bd(58), bd(56)
        );

        for (int i = 0; i < closes.size(); i++) {
            candles.add(candle(start.plusHours(i), symbol, closes.get(i)));
        }

        return candles;
    }

    private List<OHLCVData> createBollingerCandles(String symbol) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < 50; i++) {
            candles.add(candle(start.plusHours(i), symbol, bd(100 + i * 0.4)));
        }

        List<BigDecimal> closes = List.of(
            bd(115), bd(110), bd(113), bd(117), bd(120), bd(123), bd(126), bd(128)
        );
        for (int i = 0; i < closes.size(); i++) {
            candles.add(candle(start.plusHours(50 + i), symbol, closes.get(i)));
        }

        return candles;
    }

    private List<OHLCVData> createIchimokuCandles(String symbol) {
        List<OHLCVData> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.parse("2025-01-01T00:00:00");

        for (int i = 0; i < 90; i++) {
            candles.add(candle(start.plusHours(i), symbol, bd(100 + i * 1.2)));
        }

        List<BigDecimal> closes = List.of(
            bd(205), bd(202), bd(199), bd(196), bd(193), bd(190), bd(187), bd(184)
        );
        for (int i = 0; i < closes.size(); i++) {
            candles.add(candle(start.plusHours(90 + i), symbol, closes.get(i)));
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
        return new OHLCVData(
            timestamp,
            symbol,
            close,
            close.add(BigDecimal.ONE),
            close.subtract(BigDecimal.ONE),
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
