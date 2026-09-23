package com.stock.analyzer.backtest.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Position {
    private Direction direction;
    private double entryPrice;
    private double qty;
    private LocalDate entryDate;
    private int entryBar;
    private Double stopLoss;
    private Double takeProfit;
    private String comment = "";

    /** Worst price reached against the position since entry (for MAE tracking). */
    private double worstAdversePrice;

    /**
     * Best price reached in favor of the position since entry (for MFE tracking).
     */
    private double bestFavorablePrice;

    public Position(Direction direction, double entryPrice, double qty,
            LocalDate entryDate, int entryBar, Double stopLoss,
            Double takeProfit, String comment) {
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.qty = qty;
        this.entryDate = entryDate;
        this.entryBar = entryBar;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.comment = comment != null ? comment : "";
        this.worstAdversePrice = entryPrice;
        this.bestFavorablePrice = entryPrice;
    }

    /**
     * Update worst-adverse-price tracker with a bar's high/low. LONG positions
     * care about the low (worst = lowest price), SHORT about the high.
     */
    public void trackAdverse(double high, double low) {
        if (direction == Direction.LONG) {
            if (low < worstAdversePrice)
                worstAdversePrice = low;
        } else if (direction == Direction.SHORT) {
            if (high > worstAdversePrice)
                worstAdversePrice = high;
        }
    }

    /**
     * Update best-favorable-price tracker with a bar's high/low. LONG positions
     * care about the high (best = highest price), SHORT about the low.
     */
    public void trackFavorable(double high, double low) {
        if (direction == Direction.LONG) {
            if (high > bestFavorablePrice)
                bestFavorablePrice = high;
        } else if (direction == Direction.SHORT) {
            if (low < bestFavorablePrice)
                bestFavorablePrice = low;
        }
    }

    /**
     * Max Adverse Excursion in percent. Negative for LONG when price dipped below
     * entry, negative for SHORT when price spiked above entry. 0 when never
     * adverse.
     */
    public double maePct() {
        if (entryPrice <= 0)
            return 0.0;
        double diff = direction == Direction.LONG
                ? worstAdversePrice - entryPrice
                : entryPrice - worstAdversePrice;
        return (diff / entryPrice) * 100.0;
    }

    /**
     * Max Favorable Excursion in percent — the highest unrealized gain reached
     * between entry and exit, expressed against entry price. Positive when the
     * trade moved in favor, 0 if it never went above entry.
     */
    public double mfePct() {
        if (entryPrice <= 0)
            return 0.0;
        double diff = direction == Direction.LONG
                ? bestFavorablePrice - entryPrice
                : entryPrice - bestFavorablePrice;
        return (diff / entryPrice) * 100.0;
    }
}
