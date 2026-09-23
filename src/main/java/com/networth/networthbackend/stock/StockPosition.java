package com.networth.networthbackend.stock;

import java.time.LocalDate;

/**
 * One position in "My Portfolio" — open or closed — ported from the stock-analyzer's
 * {@code my_portfolio.json}. {@code stock} keeps the Yahoo suffix as stored on the VM
 * (e.g. {@code RELIANCE.NS}).
 */
public record StockPosition(
        String id,
        String market,
        String stock,
        String sector,
        String strategy,
        LocalDate entryDate,
        Double entryPrice,
        Double quantity,
        String notes,
        String status,        // open / closed
        Double exitPrice,
        LocalDate exitDate) {
}
