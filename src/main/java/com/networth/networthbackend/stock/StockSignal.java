package com.networth.networthbackend.stock;

import java.time.LocalDate;

/**
 * One current trading signal for a stock, produced by replaying its sector's winning strategy over
 * live daily bars. Mirrors the row stored in {@code stock_signals} and the JSON the analyzer API
 * returns.
 *
 * @param market "in" (NSE) or "us" (S&P 500)
 * @param type one of BUY / SELL / HOLD / FLAT
 * @param actionableTomorrow true when the signal fired on the last bar's close and fills at the
 *   next session's open (as opposed to already having filled at today's open)
 */
public record StockSignal(
        String market,
        LocalDate dataDate,
        String sector,
        String strategy,
        String stock,
        String type,
        double price,
        Double stopLoss,
        Double takeProfit,
        Double entryPrice,
        LocalDate entryDate,
        Double unrealizedPnlPct,
        String comment,
        boolean actionableTomorrow,
        Double prevClose,
        Double dayChangePct) {
}
