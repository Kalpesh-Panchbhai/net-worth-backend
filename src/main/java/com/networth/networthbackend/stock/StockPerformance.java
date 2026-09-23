package com.networth.networthbackend.stock;

/** Per-stock backtest performance summary (one row per stock) + per-trade averages. */
public record StockPerformance(
        String market,
        String sector,
        String strategy,
        String stock,
        Double cagr,
        Double mdd,
        Double wr,
        Double pf,
        Integer trades,
        Integer winners,
        Integer losers,
        Double netProfit,
        Double finalEquity,
        Double sharpe,
        Double avgBarsHeld,
        Double avgTradePct,
        Double avgMaePct,
        Double avgMfePct,
        Double avgHoldDays) {
}
