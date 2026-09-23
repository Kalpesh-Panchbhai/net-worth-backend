package com.stock.analyzer.backtest.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ClosedTrade {
    private String direction;
    private LocalDate entryDate;
    private double entryPrice;
    private LocalDate exitDate;
    private double exitPrice;
    private double qty;
    private double pnl;
    private double pnlPct;
    /**
     * Max Adverse Excursion in percent — worst unrealized loss seen between
     * entry and exit, expressed against entry price. Negative for adverse moves,
     * 0 if the trade never went underwater.
     */
    private double maePct;
    /**
     * Max Favorable Excursion in percent — best unrealized gain seen between
     * entry and exit, expressed against entry price. Positive for favorable
     * moves, 0 if the trade never went above entry.
     */
    private double mfePct;
    private int barsHeld;
    @Builder.Default
    private String entryComment = "";
    @Builder.Default
    private String exitComment = "";
    @Builder.Default
    private double commission = 0.0;
}
