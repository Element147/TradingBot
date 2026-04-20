package com.algotrader.bot.backtest.strategy;

import com.algotrader.bot.backtest.OHLCVData;
import com.algotrader.bot.entity.PositionSide;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BacktestStrategyContext(
    Map<String, List<OHLCVData>> candlesBySymbol,
    Map<String, Integer> currentIndexBySymbol,
    String primarySymbol,
    BacktestOpenPosition openPosition
) {

    public List<OHLCVData> candles() {
        return candles(primarySymbol);
    }

    public List<OHLCVData> candles(String symbol) {
        List<OHLCVData> candles = candlesBySymbol.get(symbol);
        if (candles == null) {
            throw new IllegalArgumentException("Unknown symbol in strategy context: " + symbol);
        }
        return candles;
    }

    public Set<String> symbols() {
        return candlesBySymbol.keySet();
    }

    public int currentIndex() {
        return currentIndex(primarySymbol);
    }

    public int currentIndex(String symbol) {
        return currentIndexBySymbol.getOrDefault(symbol, -1);
    }

    public boolean hasData(String symbol) {
        return currentIndex(symbol) >= 0;
    }

    public boolean hasEnoughCandles(String symbol, int minimumCandles) {
        return currentIndex(symbol) >= minimumCandles - 1;
    }

    public OHLCVData currentCandle() {
        return currentCandle(primarySymbol);
    }

    public OHLCVData currentCandle(String symbol) {
        int currentIndex = currentIndex(symbol);
        if (currentIndex < 0) {
            throw new IllegalStateException("No active candle for symbol " + symbol);
        }
        return candles(symbol).get(currentIndex);
    }

    public BigDecimal currentClose() {
        return currentClose(primarySymbol);
    }

    public BigDecimal currentClose(String symbol) {
        return currentCandle(symbol).getClose();
    }

    public boolean inPosition() {
        return openPosition != null;
    }

    public String activeSymbol() {
        return inPosition() ? openPosition.symbol() : null;
    }

    public PositionSide positionSide() {
        return inPosition() ? openPosition.side() : null;
    }

    public boolean inLongPosition() {
        return inPosition() && openPosition.side() == PositionSide.LONG;
    }

    public boolean inShortPosition() {
        return inPosition() && openPosition.side() == PositionSide.SHORT;
    }

    public int holdingBars() {
        return inPosition() ? openPosition.holdingBars() : 0;
    }
}
