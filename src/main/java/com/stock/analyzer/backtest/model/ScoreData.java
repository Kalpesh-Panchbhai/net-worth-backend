package com.stock.analyzer.backtest.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ScoreData {
    // ── Final composite score (0–100, higher = better) ──────────
    private double score;

    // ── Pillar scores (normalized 0–100) ───────────────────────
    private double calmarScore;        // Pillar 2: Calmar normalized
    private double sharpeScore;        // Pillar 1: Sharpe normalized
    private double mddScore;           // Pillar 3: MDD normalized (inverted)
    private double pfScore;            // Pillar 4: PF normalized
    private double robustnessScore;    // Pillar 5: Robustness

    // ── Raw aggregate metrics ──────────────────────────────────
    private double avgCalmar;
    private double avgSharpe;
    private double pctProfitable;
    private double consistency;

    // ── Reference metrics ──────────────────────────────────────
    private double avgCagr;
    private double avgMaxDd;
    private double avgPf;
    private double avgWr;
    private int numAssets;
    private int totalAssets;
    private Map<String, AssetDetail> perAsset;

    @Data
    @Builder
    public static class AssetDetail {
        private double cagr;
        private double totalReturnPct;
        private double maxDdPct;
        private double profitFactor;
        private double winRate;
        private int totalTrades;
        private double calmar;
        private double sharpe;
        private double finalEquity;
        private double netProfit;
    }
}
