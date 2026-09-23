package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.ranking.Scorer;
import com.stock.analyzer.backtest.strategy.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * Multi-window OOS validator for the 16 sector-winning strategies.
 *
 * The 16 winners were selected on the full 2018-onwards window (a 4,900-variant
 * search). To test the selection against two independent failure modes we run
 * each winner on four windows in one pass:
 *
 *   IS_FULL     2018-01-01 → today        — the selection window (sanity baseline)
 *   IS_EARLY    2018-01-01 → 2023-12-31   — Phase 2 in-sample sub-window
 *   OOS_HIST    2012-01-01 → 2017-12-31   — Phase 1 OOS (catches curve-fitting)
 *   OOS_RECENT  2024-01-01 → today        — Phase 2 OOS (catches regime drift)
 *
 * Phase 1 verdict = IS_FULL vs OOS_HIST.
 * Phase 2 verdict = IS_EARLY vs OOS_RECENT.
 * Combined verdict folds both:
 *   DEPLOY        both phases ROBUST
 *   DANGER        Phase 1 ROBUST but Phase 2 FAILED — regime drift, do NOT trade
 *   WATCH         mixed: one ROBUST, the other inconclusive or failing in a soft way
 *   AVOID         both phases FAILED
 *   INSUFFICIENT  not enough trades in either OOS window to judge
 *
 * Outputs (under {@code sector_csv_oos/}):
 *   {sector}_IS_perstock.csv         per-stock metrics in IS_FULL
 *   {sector}_IS_early_perstock.csv   per-stock metrics in IS_EARLY
 *   {sector}_OOS_perstock.csv        per-stock metrics in OOS_HIST
 *   {sector}_OOS_recent_perstock.csv per-stock metrics in OOS_RECENT
 *   IS_vs_OOS_summary.csv            one row per sector, all 4 windows + 3 verdicts
 *   skipped_stocks.csv               every (sector, stock, window) skip with reason
 *
 * Note on selection bias: we DID NOT re-pick winners on IS_EARLY. A strict
 * walk-forward would re-optimise the 4,900-variant search on 2018-2023 and
 * carry the new winner into OOS_RECENT. Without re-optimisation we are
 * instead asking the weaker question "does the existing winner still work
 * recently?" — which is still informative for deployment but does not fully
 * isolate optimisation-bias from regime drift.
 */
public class SectorOosValidator {

    // Winners under MDD-priority composite WITH minCAGR ≥ 8% floor on every
    // window. Forces strategies that are both low-MDD AND productive enough
    // to beat fixed-deposit rates.
    private static final String[][] SECTORS = {
        {"capital_goods",                 "R33:TONLY_ADX30_TR3_A7"},
        {"power",                         "R33:TONLY_ADX25_TR3_A7"},
        {"information_technology",        "R34:TONLY_ADX33_TR4.0_A8"},
        {"automobile_and_auto_components","R34:TONLY_ADX28_TR5.0_A8"},
        {"fast_moving_consumer_goods",    "R33:TONLY_ADX20_TR3_A7"},
        {"consumer_durables",             "R34:TONLY_ADX33_TR4.0_A8"},
        {"financial_services",            "R34:TONLY_ADX28_TR4.0_A8"},
        {"healthcare",                    "R34:TONLY_ADX30_TR4.0_A8"},
        {"chemicals",                     "R33:TONLY_ADX20_TR3_A7"},
        {"consumer_services",             "R34:TONLY_ADX33_TR4.5_A7"},
        {"realty",                        "R33:PB_E15_TR0.3_A3"},
        {"metals_and_mining",             "R33:TONLY_ADX20_TR3_A7"},
        {"services",                      "R34:TONLY_ADX30_TR5.0_A8"},
        {"construction",                  "AS7:RSI35_MT7"},
        {"oil_gas_and_consumable_fuels",  "R33:TONLY_ADX30_TR3_A7"},
        {"telecommunication",             "R33:TONLY_ADX30_TR3_A7"},
    };

    private static final String OUT_DIR = "sector_csv_oos";
    private static final String PERSTOCK_HEADER =
        "Stock,Trades,Winners,Losers,WR%,CAGR%,TotalReturn%,MaxDD%,PF,Sharpe,Calmar,NetProfit,FinalEquity";

    /** A named backtest window. {@code to == null} means "up to today". */
    private static final class Window {
        final String label;
        final String fileSuffix;
        final LocalDate from;
        final LocalDate to;

        Window(String label, String fileSuffix, String fromIso, String toIsoOrNull) {
            this.label = label;
            this.fileSuffix = fileSuffix;
            this.from = LocalDate.parse(fromIso);
            this.to = toIsoOrNull != null ? LocalDate.parse(toIsoOrNull) : null;
        }

        LocalDate effectiveEnd() { return to != null ? to : LocalDate.now(); }
        double years()           { return ChronoUnit.DAYS.between(from, effectiveEnd()) / 365.25; }
        String shortRange()      { return from + " → " + (to != null ? to : "today"); }
    }

    public static void main(String[] args) throws Exception {
        Path outDir = Paths.get(OUT_DIR);
        Files.createDirectories(outDir);

        Window IS_FULL    = new Window("IS_FULL",    "IS",
            BacktestConfig.TRADE_FROM,      null);
        Window IS_EARLY   = new Window("IS_EARLY",   "IS_early",
            BacktestConfig.TRADE_FROM,      BacktestConfig.IS_EARLY_TO);
        Window OOS_HIST   = new Window("OOS_HIST",   "OOS",
            BacktestConfig.OOS_TRADE_FROM,  BacktestConfig.OOS_TRADE_TO);
        Window OOS_RECENT = new Window("OOS_RECENT", "OOS_recent",
            BacktestConfig.OOS_RECENT_FROM, null);

        Window[] windows = {IS_FULL, IS_EARLY, OOS_HIST, OOS_RECENT};

        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();

        System.out.printf("%n  Windows:%n");
        for (Window w : windows) {
            System.out.printf("    %-12s %-30s (%.2f yrs)%n", w.label, w.shortRange(), w.years());
        }
        System.out.println();
        System.out.println("  " + "-".repeat(130));
        System.out.printf("  %-32s %-28s %10s %10s %10s %10s   %-12s%n",
            "Sector", "Strategy",
            "IS_FULL", "IS_EARLY", "OOS_HIST", "OOS_RECENT", "Verdict");
        System.out.printf("  %-32s %-28s %10s %10s %10s %10s%n",
            "", "", "CAGR%", "CAGR%", "CAGR%", "CAGR%");
        System.out.println("  " + "-".repeat(130));

        try (BufferedWriter summary = Files.newBufferedWriter(outDir.resolve("IS_vs_OOS_summary.csv"));
             BufferedWriter skipped = Files.newBufferedWriter(outDir.resolve("skipped_stocks.csv"))) {

            summary.write(buildSummaryHeader(windows));
            summary.newLine();
            skipped.write("Sector,Stock,Window,Reason");
            skipped.newLine();

            for (String[] info : SECTORS) {
                String sectorName = info[0];
                String stratName  = info[1];

                Supplier<Strategy> factory = registry.get(stratName);
                if (factory == null) {
                    System.err.printf("  ✗ Strategy not found: %s%n", stratName);
                    continue;
                }

                Path sectorFile = Paths.get("src/main/resources/sectors/" + sectorName + ".txt");
                if (!Files.exists(sectorFile)) {
                    System.err.printf("  ✗ Sector file missing: %s%n", sectorFile);
                    continue;
                }
                List<String> symbols = Files.readAllLines(sectorFile);
                symbols.removeIf(s -> s == null || s.trim().isEmpty());

                LinkedHashMap<Window, Map<String, Metrics>> metricsByWindow = new LinkedHashMap<>();
                LinkedHashMap<Window, BufferedWriter> csvByWindow            = new LinkedHashMap<>();
                for (Window w : windows) {
                    metricsByWindow.put(w, new LinkedHashMap<>());
                    BufferedWriter writer = Files.newBufferedWriter(
                        outDir.resolve(sectorName + "_" + w.fileSuffix + "_perstock.csv"));
                    writer.write(PERSTOCK_HEADER);
                    writer.newLine();
                    csvByWindow.put(w, writer);
                }

                try {
                    for (String symbol : symbols) {
                        List<Bar> bars = fetcher.fetchData(symbol, BacktestConfig.WARMUP_START);
                        if (bars == null || bars.size() < 50) {
                            skipped.write(String.format("%s,%s,ALL,no_data_or_too_short", sectorName, symbol));
                            skipped.newLine();
                            continue;
                        }

                        for (Window w : windows) {
                            // IS_FULL keeps original behaviour (no min-bars-in-window filter);
                            // all sub-windows require >= OOS_MIN_WINDOW_BARS so we don't aggregate
                            // late-IPO stubs.
                            if (w != IS_FULL) {
                                int barsInWindow = countBarsInWindow(bars, w.from, w.effectiveEnd());
                                if (barsInWindow < BacktestConfig.OOS_MIN_WINDOW_BARS) {
                                    skipped.write(String.format("%s,%s,%s,window_bars=%d_min=%d",
                                        sectorName, symbol, w.label,
                                        barsInWindow, BacktestConfig.OOS_MIN_WINDOW_BARS));
                                    skipped.newLine();
                                    continue;
                                }
                            }

                            Metrics m = runWindow(factory.get(), bars, w.from, w.to);
                            metricsByWindow.get(w).put(symbol, m);
                            writePerStock(csvByWindow.get(w), symbol, m, w.years());
                        }
                    }
                } finally {
                    for (BufferedWriter w : csvByWindow.values()) w.close();
                }

                LinkedHashMap<Window, ScoreData> scoreByWindow = new LinkedHashMap<>();
                LinkedHashMap<Window, Integer>   totalByWindow = new LinkedHashMap<>();
                for (Window w : windows) {
                    Map<String, Metrics> map = metricsByWindow.get(w);
                    scoreByWindow.put(w, Scorer.computeScore(map, w.years()));
                    totalByWindow.put(w, map.size());
                }

                String phase1   = verdict(scoreByWindow.get(IS_FULL),  scoreByWindow.get(OOS_HIST),
                                          totalByWindow.get(IS_FULL),  totalByWindow.get(OOS_HIST));
                String phase2   = verdict(scoreByWindow.get(IS_EARLY), scoreByWindow.get(OOS_RECENT),
                                          totalByWindow.get(IS_EARLY), totalByWindow.get(OOS_RECENT));
                String combined = combinedVerdict(phase1, phase2);

                summary.write(buildSummaryRow(sectorName, stratName, windows,
                    scoreByWindow, totalByWindow, phase1, phase2, combined));
                summary.newLine();

                System.out.printf("  %-32s %-28s %+9.2f%% %+9.2f%% %+9.2f%% %+9.2f%%   %s%n",
                    sectorName, stratName,
                    scoreByWindow.get(IS_FULL).getAvgCagr(),
                    scoreByWindow.get(IS_EARLY).getAvgCagr(),
                    scoreByWindow.get(OOS_HIST).getAvgCagr(),
                    scoreByWindow.get(OOS_RECENT).getAvgCagr(),
                    combined + " [P1=" + phase1 + ", P2=" + phase2 + "]");
            }

            System.out.println("  " + "-".repeat(130));
            System.out.printf("%n  Summary : %s%n", outDir.resolve("IS_vs_OOS_summary.csv"));
            System.out.printf("  Skipped : %s%n", outDir.resolve("skipped_stocks.csv"));
            System.out.printf("  Per-stock CSVs: %s/{sector}_{IS,IS_early,OOS,OOS_recent}_perstock.csv%n", outDir);
        }
    }

    // ── Single-window run ────────────────────────────────────────────

    private static Metrics runWindow(Strategy strategy, List<Bar> bars,
                                     LocalDate tradeFrom, LocalDate tradeToOrNull) {
        StrategyData data = strategy.computeIndicators(bars);

        TradingViewEngine engine = new TradingViewEngine(
            BacktestConfig.INITIAL_CAPITAL,
            BacktestConfig.COMMISSION_PCT,
            BacktestConfig.SLIPPAGE,
            "percent_of_equity",
            100.0,
            0
        );

        Bar lastProcessed = null;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            // Hard truncate at window end so positions can't bleed into a
            // later phase via a 2017 entry that exits in 2018.
            if (tradeToOrNull != null && bar.getDate().isAfter(tradeToOrNull)) break;

            engine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(),
                bar.getLow(), bar.getClose(), i);

            boolean tradeActive = !bar.getDate().isBefore(tradeFrom);
            strategy.onBar(engine, data, i, tradeActive);

            lastProcessed = bar;
        }

        if (lastProcessed != null) {
            engine.finalize(lastProcessed.getDate(), lastProcessed.getClose());
        }
        return engine.getMetrics();
    }

    private static int countBarsInWindow(List<Bar> bars, LocalDate from, LocalDate to) {
        int count = 0;
        for (Bar b : bars) {
            LocalDate d = b.getDate();
            if (!d.isBefore(from) && !d.isAfter(to)) count++;
        }
        return count;
    }

    // ── Verdict logic ────────────────────────────────────────────────

    private static String verdict(ScoreData is, ScoreData oos, int isTotal, int oosTotal) {
        if (oos.getNumAssets() == 0) {
            return oosTotal == 0 ? "NO_OOS_DATA" : "INSUFFICIENT_TRADES";
        }
        double oosCagr = oos.getAvgCagr();
        double oosPf   = oos.getAvgPf();
        double oosPct  = oos.getPctProfitable();
        double isCagr  = is.getAvgCagr();

        if (oosCagr <= 0 || oosPf < 1.0 || oosPct < 0.30) return "FAILED";
        if (oosPf < 1.3 || (isCagr > 0 && oosCagr < isCagr * 0.30)) return "DEGRADED";
        if (oosPf >= 1.5 && (isCagr <= 0 || oosCagr >= isCagr * 0.70)) return "ROBUST*";
        return "ROBUST";
    }

    private static boolean isPass(String v)       { return "ROBUST".equals(v) || "ROBUST*".equals(v); }
    private static boolean isFail(String v)       { return "FAILED".equals(v) || "DEGRADED".equals(v); }
    private static boolean isInconclusive(String v) {
        return "INSUFFICIENT_TRADES".equals(v) || "NO_OOS_DATA".equals(v);
    }

    private static String combinedVerdict(String phase1, String phase2) {
        if (isPass(phase1) && isPass(phase2))             return "DEPLOY";
        if (isPass(phase1) && isFail(phase2))             return "DANGER";   // regime drift
        if (isFail(phase1) && isPass(phase2))             return "WATCH";    // recent rescue
        if (isFail(phase1) && isFail(phase2))             return "AVOID";
        if (isInconclusive(phase1) && isInconclusive(phase2)) return "INSUFFICIENT";
        if (isPass(phase1) || isPass(phase2))             return "WATCH";    // one pass + one unclear
        return "AVOID";                                                       // one fail + one unclear
    }

    // ── CSV helpers ──────────────────────────────────────────────────

    private static String buildSummaryHeader(Window[] windows) {
        StringBuilder sb = new StringBuilder("Sector,Strategy");
        for (Window w : windows) {
            String p = w.label;
            sb.append(',').append(p).append("_Score")
              .append(',').append(p).append("_CAGR%")
              .append(',').append(p).append("_MaxDD%")
              .append(',').append(p).append("_WR%")
              .append(',').append(p).append("_PF")
              .append(',').append(p).append("_Sharpe")
              .append(',').append(p).append("_Calmar")
              .append(',').append(p).append("_PctProf%")
              .append(',').append(p).append("_StocksUsed")
              .append(',').append(p).append("_StocksTotal");
        }
        sb.append(",Phase1_Verdict,Phase2_Verdict,Combined_Verdict");
        return sb.toString();
    }

    private static String buildSummaryRow(String sector, String strategy, Window[] windows,
                                          Map<Window, ScoreData> scoreByWindow,
                                          Map<Window, Integer>   totalByWindow,
                                          String phase1, String phase2, String combined) {
        StringBuilder sb = new StringBuilder();
        sb.append(sector).append(',').append(strategy);
        for (Window w : windows) {
            ScoreData s = scoreByWindow.get(w);
            int total   = totalByWindow.get(w);
            sb.append(String.format(Locale.US, ",%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.2f,%d,%d",
                s.getScore(), s.getAvgCagr(), s.getAvgMaxDd(), s.getAvgWr(),
                s.getAvgPf(), s.getAvgSharpe(), s.getAvgCalmar(),
                s.getPctProfitable() * 100,
                s.getNumAssets(), total));
        }
        sb.append(',').append(phase1).append(',').append(phase2).append(',').append(combined);
        return sb.toString();
    }

    private static void writePerStock(BufferedWriter out, String symbol, Metrics m, double yrs)
            throws IOException {
        double cagrPct = Scorer.cagr(m.getTotalReturnPct(), yrs);
        double dd      = Math.abs(m.getMaxDrawdownPct());
        double calmar  = (cagrPct > 0 && dd > 0) ? cagrPct / dd : 0;
        double sharpe  = (Double.isNaN(m.getSharpeRatio()) || Double.isInfinite(m.getSharpeRatio()))
                            ? 0 : m.getSharpeRatio();
        double pf      = (Double.isInfinite(m.getProfitFactor()) || Double.isNaN(m.getProfitFactor())
                            || m.getProfitFactor() > 10.0) ? 10.0 : m.getProfitFactor();

        out.write(String.format(Locale.US, "%s,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.2f,%.2f",
            symbol,
            m.getTotalTrades(),
            m.getWinningTrades(),
            m.getLosingTrades(),
            m.getWinRate(),
            cagrPct,
            m.getTotalReturnPct(),
            -dd,
            pf,
            sharpe,
            calmar,
            m.getNetProfit(),
            m.getFinalEquity()));
        out.newLine();
    }
}
