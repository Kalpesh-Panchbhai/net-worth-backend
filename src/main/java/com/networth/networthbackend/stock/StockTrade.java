package com.networth.networthbackend.stock;

import java.time.LocalDate;

/** One backtest trade from all_trades.csv. */
public record StockTrade(
        String market,
        String sector,
        String strategy,
        String stock,
        int tradeNum,
        LocalDate entryDate,
        LocalDate exitDate,
        Double entryPrice,
        Double exitPrice,
        Double pnl,
        Double pnlPct,
        Double maePct,
        Double mfePct,
        Integer barsHeld,
        String comment) {
}
