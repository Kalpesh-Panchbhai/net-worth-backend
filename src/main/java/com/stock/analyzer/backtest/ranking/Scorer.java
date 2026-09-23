package com.stock.analyzer.backtest.ranking;

import com.stock.analyzer.backtest.model.Metrics;
import com.stock.analyzer.backtest.model.ScoreData;
import com.stock.analyzer.backtest.model.StrategyResult;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Ranking & Scoring System — Industry-standard 3-pillar composite.
 *
 * 3 pillars, each normalized 0–100 via rank-percentile across the strategy universe.
 * MDD and PF are dropped because they're already captured by Sharpe + Calmar:
 *   - Sharpe captures volatility-adjusted return (penalizes MDD via stddev)
 *   - Calmar captures CAGR/MDD directly
 *   - PF correlates strongly with Calmar (winners/losers ratio ↔ CAGR/MDD)
 *
 *   Pillar 1 (55%) — Sharpe Ratio: risk-adjusted return (industry primary)
 *   Pillar 2 (35%) — Calmar Ratio (CAGR / |MaxDD|): high CAGR + low drawdown
 *   Pillar 3 (10%) — Robustness: cross-asset consistency
 *
 * Score = 0.55×Sharpe + 0.35×Calmar + 0.10×Robustness
 *
 * Each metric is rank-percentile normalized across ALL strategies in the run —
 * percentile gives intuitive "what % of strategies am I better than" score.
 */
public class Scorer {

    // ── Constants ─────────────────────────────────────────────────

    private static final double W_SHARPE  = 0.55;
    private static final double W_CALMAR  = 0.35;
    private static final double W_MDD     = 0.00;   // dropped — captured by Sharpe + Calmar
    private static final double W_PF      = 0.00;   // dropped — correlates with Calmar
    private static final double W_ROBUST  = 0.10;

    // Absolute thresholds for single-strategy fallback normalization
    private static final double SHARPE_EXCELLENT = 2.0;   // Sharpe ≥ 2.0 → score 100
    private static final double CALMAR_EXCELLENT = 1.0;   // Calmar ≥ 1.0 → score 100
    private static final double MDD_BEST = 10.0;          // |MDD| ≤ 10% → score 100
    private static final double MDD_WORST = 60.0;         // |MDD| ≥ 60% → score 0
    private static final double PF_EXCELLENT = 3.0;       // PF ≥ 3.0 → score 100
    private static final double COV_CAP = 50.0;
    private static final int MIN_TRADES = 5;

    // ── CAGR ──────────────────────────────────────────────────────

    public static double cagr(double totalReturnPct, double years) {
        if (years <= 0) return 0.0;
        double totalMult = 1.0 + totalReturnPct / 100.0;
        if (totalMult <= 0) return -100.0;
        return (Math.pow(totalMult, 1.0 / years) - 1.0) * 100.0;
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    // ── Scoring ───────────────────────────────────────────────────

    /**
     * Compute raw aggregate metrics for a single strategy.
     * Score is initially set using absolute thresholds (fallback for single-strategy runs).
     * Call normalizeScores() after all strategies are scored for proper cross-strategy ranking.
     */
    public static ScoreData computeScore(Map<String, Metrics> perAssetMetrics, double years) {
        if (perAssetMetrics == null || perAssetMetrics.isEmpty()) return emptyResult();

        int totalAssetsTested = perAssetMetrics.size();
        List<Double> cagrs = new ArrayList<>();
        List<Double> calmars = new ArrayList<>();
        List<Double> sharpes = new ArrayList<>();
        List<Double> maxDds = new ArrayList<>();
        List<Double> pfs = new ArrayList<>();
        List<Double> wrs = new ArrayList<>();
        Map<String, ScoreData.AssetDetail> perAsset = new LinkedHashMap<>();

        for (var entry : perAssetMetrics.entrySet()) {
            String symbol = entry.getKey();
            Metrics m = entry.getValue();

            double assetCagr = cagr(m.getTotalReturnPct(), years);
            double assetDd = Math.abs(m.getMaxDrawdownPct());
            double assetPf = (Double.isInfinite(m.getProfitFactor()) || Double.isNaN(m.getProfitFactor()) || m.getProfitFactor() > 10.0) ? 10.0 : m.getProfitFactor();
            double assetWr = m.getWinRate();
            double assetSharpe = Double.isNaN(m.getSharpeRatio()) || Double.isInfinite(m.getSharpeRatio()) ? 0 : m.getSharpeRatio();

            double assetCalmar = 0;
            if (assetCagr > 0 && assetDd > 0) {
                assetCalmar = assetCagr / assetDd;
            }

            if (m.getTotalTrades() >= MIN_TRADES) {
                cagrs.add(assetCagr);
                calmars.add(assetCalmar);
                sharpes.add(assetSharpe);
                maxDds.add(assetDd);
                pfs.add(assetPf);
                wrs.add(assetWr);
            }

            perAsset.put(symbol, ScoreData.AssetDetail.builder()
                    .cagr(round2(assetCagr))
                    .totalReturnPct(round2(m.getTotalReturnPct()))
                    .maxDdPct(round2(-assetDd))
                    .profitFactor(round4(assetPf))
                    .winRate(round2(assetWr))
                    .totalTrades(m.getTotalTrades())
                    .calmar(round4(assetCalmar))
                    .sharpe(round4(assetSharpe))
                    .finalEquity(round2(m.getFinalEquity()))
                    .netProfit(round2(m.getNetProfit()))
                    .build());
        }

        int n = cagrs.size();
        if (n == 0) return emptyResult();

        double avgCagr = cagrs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgCalmar = calmars.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgSharpe = sharpes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgDd = maxDds.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgPf = pfs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgWr = wrs.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Robustness: cross-asset consistency
        long profitableCount = cagrs.stream().filter(c -> c > 0).count();
        double pctProfitable = n > 0 ? (double) profitableCount / n : 0;

        double consistency;
        if (n >= 10) {
            List<Double> sorted = new ArrayList<>(cagrs);
            Collections.sort(sorted);
            int trimCount = Math.max(1, (int) (n * 0.45));
            List<Double> trimmed = sorted.subList(trimCount, n - trimCount);
            double tMean = trimmed.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (tMean != 0 && trimmed.size() >= 2) {
                double tSumSq = trimmed.stream().mapToDouble(c -> (c - tMean) * (c - tMean)).sum();
                double tStd = Math.sqrt(tSumSq / (trimmed.size() - 1));
                double tCov = tStd / Math.abs(tMean);
                consistency = clamp(1.0 - tCov / COV_CAP, 0, 1);
            } else {
                consistency = 0.0;
            }
        } else if (n >= 2 && avgCagr != 0) {
            double mean = avgCagr;
            double sumSq = cagrs.stream().mapToDouble(c -> (c - mean) * (c - mean)).sum();
            double std = Math.sqrt(sumSq / (n - 1));
            double cov = std / Math.abs(mean);
            consistency = clamp(1.0 - cov / COV_CAP, 0, 1);
        } else {
            consistency = n == 1 ? 1.0 : 0.0;
        }

        double robustnessScore = clamp((pctProfitable * 0.05 + consistency * 0.95) * 100, 0, 100);

        // Fallback absolute scoring (for single-strategy runs)
        double sharpeScoreAbs = clamp((avgSharpe / SHARPE_EXCELLENT) * 100, 0, 100);
        double calmarScoreAbs = clamp((avgCalmar / CALMAR_EXCELLENT) * 100, 0, 100);
        double mddScoreAbs = clamp((MDD_WORST - avgDd) / (MDD_WORST - MDD_BEST) * 100, 0, 100);
        double pfScoreAbs = clamp(((avgPf - 1.0) / (PF_EXCELLENT - 1.0)) * 100, 0, 100);

        double fallbackScore = W_SHARPE * sharpeScoreAbs + W_CALMAR * calmarScoreAbs
                             + W_MDD * mddScoreAbs + W_PF * pfScoreAbs + W_ROBUST * robustnessScore;

        return ScoreData.builder()
                .score(round2(fallbackScore))
                .sharpeScore(round2(sharpeScoreAbs))
                .calmarScore(round2(calmarScoreAbs))
                .mddScore(round2(mddScoreAbs))
                .pfScore(round2(pfScoreAbs))
                .robustnessScore(round2(robustnessScore))
                .avgCalmar(round4(avgCalmar))
                .avgSharpe(round4(avgSharpe))
                .pctProfitable(round4(pctProfitable))
                .consistency(round4(consistency))
                .avgCagr(round2(avgCagr))
                .avgMaxDd(round2(-avgDd))
                .avgPf(round4(avgPf))
                .avgWr(round2(avgWr))
                .numAssets(n)
                .totalAssets(totalAssetsTested)
                .perAsset(perAsset)
                .build();
    }

    /**
     * Cross-strategy normalization: rank-percentile normalize each pillar across all strategies,
     * then compute weighted composite score. Rank-based is robust to outliers and gives
     * intuitive percentile-of-universe scores (e.g., 95 = top 5% on that pillar).
     */
    public static void normalizeScores(List<ScoreData> allScores) {
        if (allScores == null || allScores.size() <= 1) return;

        double[] sharpes = allScores.stream().mapToDouble(ScoreData::getAvgSharpe).toArray();
        double[] calmars = allScores.stream().mapToDouble(ScoreData::getAvgCalmar).toArray();
        double[] mdds = allScores.stream().mapToDouble(s -> Math.abs(s.getAvgMaxDd())).toArray();
        double[] pfs = allScores.stream().mapToDouble(ScoreData::getAvgPf).toArray();
        double[] robust = allScores.stream().mapToDouble(ScoreData::getRobustnessScore).toArray();

        // Rank-percentile normalize (higher = better for all except MDD which is inverted)
        double[] nSharpe = rankPercentileNorm(sharpes, false);
        double[] nCalmar = rankPercentileNorm(calmars, false);
        double[] nMdd    = rankPercentileNorm(mdds, true);    // inverted: lower MDD = higher score
        double[] nPf     = rankPercentileNorm(pfs, false);
        double[] nRobust = rankPercentileNorm(robust, false);

        for (int i = 0; i < allScores.size(); i++) {
            ScoreData sd = allScores.get(i);
            double s = nSharpe[i] * 100;
            double c = nCalmar[i] * 100;
            double m = nMdd[i] * 100;
            double p = nPf[i] * 100;
            double r = nRobust[i] * 100;

            sd.setSharpeScore(round2(s));
            sd.setCalmarScore(round2(c));
            sd.setMddScore(round2(m));
            sd.setPfScore(round2(p));
            sd.setRobustnessScore(round2(r));
            sd.setScore(round2(W_SHARPE * s + W_CALMAR * c + W_MDD * m + W_PF * p + W_ROBUST * r));
        }
    }

    /**
     * Rank-percentile normalization: returns the percentile-rank (0..1) of each value.
     * Top value → 1.0, bottom → 0.0, median → 0.5. Ties get average rank.
     * If inverted, lower raw value → higher percentile.
     */
    private static double[] rankPercentileNorm(double[] vals, boolean inverted) {
        int n = vals.length;
        // Create indices sorted by value
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        if (inverted) {
            Arrays.sort(idx, (a, b) -> Double.compare(vals[b], vals[a]));
        } else {
            Arrays.sort(idx, (a, b) -> Double.compare(vals[a], vals[b]));
        }

        double[] out = new double[n];
        // Assign average rank for ties
        int i = 0;
        while (i < n) {
            int j = i;
            double v = vals[idx[i]];
            while (j < n && vals[idx[j]] == v) j++;
            // Average rank = (i + (j-1)) / 2 (0-indexed)
            double avgRank = (i + j - 1) / 2.0;
            // Percentile: top gets 1.0, bottom gets 0.0
            double pct = n > 1 ? avgRank / (n - 1) : 0.5;
            for (int k = i; k < j; k++) out[idx[k]] = pct;
            i = j;
        }
        return out;
    }

    private static ScoreData emptyResult() {
        return ScoreData.builder()
                .score(0).sharpeScore(0).calmarScore(0).mddScore(0).pfScore(0)
                .robustnessScore(0).avgCalmar(0).avgSharpe(0)
                .pctProfitable(0).consistency(0)
                .avgCagr(0).avgMaxDd(0).avgPf(0).avgWr(0)
                .numAssets(0).totalAssets(0)
                .perAsset(new LinkedHashMap<>())
                .build();
    }

    // ── XLSX Writer ───────────────────────────────────────────────

    public static void writeRankings(List<StrategyResult> results, String filepath) throws IOException {
        results = new ArrayList<>(results);
        results.sort((a, b) -> Double.compare(b.getScoreData().getScore(), a.getScoreData().getScore()));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle scoreStyle = wb.createCellStyle();
            Font scoreFont = wb.createFont();
            scoreFont.setBold(true);
            scoreStyle.setFont(scoreFont);

            // ── Sheet 1: Rankings ─────────────────────────────────
            Sheet ws = wb.createSheet("Rankings");
            String[] headers = {"Rank", "Strategy", "Category", "Timeframe", "Score",
                    "Sharpe Score", "Calmar Score", "MDD Score", "PF Score", "Robust Score",
                    "Avg Sharpe", "Avg Calmar",
                    "% Profitable", "Consistency",
                    "Avg CAGR %", "Avg MaxDD %", "Avg PF", "Avg WR %", "Assets"};

            Row headerRow = ws.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            for (int rank = 0; rank < results.size(); rank++) {
                StrategyResult r = results.get(rank);
                ScoreData sd = r.getScoreData();
                Row row = ws.createRow(rank + 1);
                row.createCell(0).setCellValue(rank + 1);
                row.createCell(1).setCellValue(r.getStrategyName());
                row.createCell(2).setCellValue(categorize(r.getStrategyName()));
                row.createCell(3).setCellValue(r.getTimeframe());
                Cell sc = row.createCell(4);
                sc.setCellValue(sd.getScore());
                sc.setCellStyle(scoreStyle);
                row.createCell(5).setCellValue(sd.getSharpeScore());
                row.createCell(6).setCellValue(sd.getCalmarScore());
                row.createCell(7).setCellValue(sd.getMddScore());
                row.createCell(8).setCellValue(sd.getPfScore());
                row.createCell(9).setCellValue(sd.getRobustnessScore());
                row.createCell(10).setCellValue(sd.getAvgSharpe());
                row.createCell(11).setCellValue(sd.getAvgCalmar());
                row.createCell(12).setCellValue(sd.getPctProfitable() * 100);
                row.createCell(13).setCellValue(sd.getConsistency() * 100);
                row.createCell(14).setCellValue(sd.getAvgCagr());
                row.createCell(15).setCellValue(sd.getAvgMaxDd());
                row.createCell(16).setCellValue(sd.getAvgPf());
                row.createCell(17).setCellValue(sd.getAvgWr());
                row.createCell(18).setCellValue(sd.getNumAssets());
            }

            for (int c = 0; c < headers.length; c++) ws.autoSizeColumn(c);

            // ── Sheet 2: Per-Asset Detail (skip if too large to avoid OOM) ──
            long estimatedRows = results.stream()
                    .mapToLong(r -> r.getScoreData().getPerAsset() != null ? r.getScoreData().getPerAsset().size() : 0)
                    .sum();
            if (estimatedRows <= 50000) {
                Sheet ws2 = wb.createSheet("Detail");
                String[] dHeaders = {"Strategy", "Timeframe", "Asset",
                        "CAGR %", "Calmar", "Total Return %", "MaxDD %", "PF",
                        "Win Rate %", "Trades", "Sharpe", "Net Profit", "Final Equity"};

                Row dHeaderRow = ws2.createRow(0);
                for (int c = 0; c < dHeaders.length; c++) {
                    Cell cell = dHeaderRow.createCell(c);
                    cell.setCellValue(dHeaders[c]);
                    cell.setCellStyle(headerStyle);
                }

                int detailRow = 1;
                for (StrategyResult r : results) {
                    ScoreData sd = r.getScoreData();
                    if (sd.getPerAsset() == null) continue;
                    for (var ae : sd.getPerAsset().entrySet()) {
                        ScoreData.AssetDetail ad = ae.getValue();
                        Row row = ws2.createRow(detailRow++);
                        row.createCell(0).setCellValue(r.getStrategyName());
                        row.createCell(1).setCellValue(r.getTimeframe());
                        row.createCell(2).setCellValue(ae.getKey());
                        row.createCell(3).setCellValue(ad.getCagr());
                        row.createCell(4).setCellValue(ad.getCalmar());
                        row.createCell(5).setCellValue(ad.getTotalReturnPct());
                        row.createCell(6).setCellValue(ad.getMaxDdPct());
                        row.createCell(7).setCellValue(ad.getProfitFactor());
                        row.createCell(8).setCellValue(ad.getWinRate());
                        row.createCell(9).setCellValue(ad.getTotalTrades());
                        row.createCell(10).setCellValue(ad.getSharpe());
                        row.createCell(11).setCellValue(ad.getNetProfit());
                        row.createCell(12).setCellValue(ad.getFinalEquity());
                    }
                }
                for (int c = 0; c < dHeaders.length; c++) ws2.autoSizeColumn(c);
            } else {
                System.out.printf("  Skipping Detail sheet (%,d rows would exceed limit)%n", estimatedRows);
            }

            try (FileOutputStream out = new FileOutputStream(filepath)) {
                wb.write(out);
            }
            System.out.println("\n  Rankings saved to " + filepath);
        }
    }

    private static String categorize(String name) {
        if (name.startsWith("SMCL_R")) return "SMC Lite";
        if (name.startsWith("V3_R")) return "SMC V3";
        if (name.startsWith("V2_R")) return "SMC V2";
        if (name.startsWith("SMC_R3:")) return "Enhanced SMC";
        if (name.startsWith("SMC_R2:")) return "Enhanced SMC";
        if (name.startsWith("SMC_R1:")) return "Enhanced SMC";
        if (name.startsWith("SMC ") || name.startsWith("SMC_")) return "SMC Base";
        if (name.startsWith("HA_R")) return "HA Enhanced";
        if (name.startsWith("MRT_")) return "Mean Rev Trend";
        if (name.startsWith("AMF_")) return "Adaptive Multi";
        if (name.contains("Dual Mom") || name.contains("Dual Momentum") || name.contains("DMOM")) return "Quant";
        if (name.contains("Donchian") || name.contains("Heikin") || name.contains("Mean Revert")
                || name.contains("Chandelier") || name.contains("Vol Breakout")
                || name.contains("Keltner")) return "Quant";
        return "Other";
    }

    private static double round2(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v)) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }
    private static double round4(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v)) return 0.0;
        return Math.round(v * 10000.0) / 10000.0;
    }
}
