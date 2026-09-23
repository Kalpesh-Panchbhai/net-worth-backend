package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.Bar;

import java.util.List;

/**
 * Strategy interface matching the Python pattern:
 * - name, timeframe, description attributes
 * - computeIndicators(bars) → returns enriched data arrays
 * - onBar(engine, data, i, tradeActive) → generates entry/exit signals
 * - reset() → resets internal state between symbol runs
 */
public interface Strategy {

    String getName();
    String getTimeframe();
    String getDescription();

    /**
     * Compute all indicators needed by this strategy.
     * Returns a StrategyData object containing OHLCV arrays plus indicator arrays.
     */
    StrategyData computeIndicators(List<Bar> bars);

    /**
     * Process a single bar: detect signals and issue entry/exit commands.
     *
     * @param engine The backtesting engine
     * @param data   Pre-computed indicator data
     * @param i      Current bar index
     * @param tradeActive Whether trading is active (date >= trade_from)
     */
    void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive);

    /**
     * Reset internal state (called between symbol runs).
     */
    void reset();
}
