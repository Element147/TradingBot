package com.algotrader.bot.backtest;

@FunctionalInterface
public interface BacktestSimulationProgressListener {

    void onProgress(BacktestSimulationProgress progress);
}
