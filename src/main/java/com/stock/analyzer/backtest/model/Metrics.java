package com.stock.analyzer.backtest.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Metrics {
    private double netProfit;
    private double netProfitPct;
    private double grossProfit;
    private double grossLoss;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private int breakevenTrades;
    private double winRate;
    private double profitFactor;
    private double avgTrade;
    private double avgWin;
    private double avgLoss;
    private double avgWinPct;
    private double avgLossPct;
    private double avgBarsHeld;
    private double maxWin;
    private double maxLoss;
    private double maxDrawdownPct;
    private int maxConsecutiveWins;
    private int maxConsecutiveLosses;
    private double totalCommission;
    private double finalEquity;
    private double initialCapital;
    private double totalReturnPct;
    private double sharpeRatio;
}
