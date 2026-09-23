package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.Market;
import com.stock.analyzer.backtest.config.RefinedVariants;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.ranking.SubWindowMetrics;
import com.stock.analyzer.backtest.strategy.Strategy;
import com.stock.analyzer.backtest.strategy.StrategyData;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Generates detailed per-stock performance and trade-level reports
 * for sector + strategy combinations.
 *
 * Usage:
 * Single: SectorDetailReport healthcare R34:TONLY_ADX33_TR4.5_A8
 * Batch: SectorDetailReport --all (runs all winning sectors)
 *
 * Output (in sector_detail_report/<sector>/):
 * 1. per_stock_performance.csv — CAGR, MDD, WR, PF, trades per stock
 * 2. all_trades.csv — every single trade with entry/exit dates, prices, PnL
 */
public class SectorDetailReport {

    private static final LocalDate OOS_HIST_FROM = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);

    // Single continuous period for the detail report (windows are only for strategy
    // selection)
    private static final LocalDate TRADE_FROM = OOS_HIST_FROM; // 2012-01-01

    // Synced to sector_all_phases/new_winners.csv after 2026-05-29 data refresh
    // + full 10,314-variant FullRecheck (see sector_all_phases/full_recheck_*.csv).
    private static final String[][] SECTOR_WINNERS = {
            { "automobile_and_auto_components", "R41:RSI43_ADX32_TR5_W8_TP8_CS" },
            { "capital_goods", "R40:PB_ADX13_CD20_TR5_A5_TP30" },
            { "chemicals", "R40:TONLY_ADX26_TR2.5_W35" },
            { "construction", "R40:RSI40_ADX25_TR3_W25_TP15" },
            { "construction_materials", "R38:TONLY_ADX33_TR4.5_A6_TP30" },
            { "consumer_durables", "R40:TONLY_ADX33_TR3_W30_TP20" },
            { "consumer_services", "R41:RSI47_ADX31_TR5_W12_TP10_CS" },
            { "fast_moving_consumer_goods", "R41:TONLY_ADX20_TR3_W20_RE" },
            { "financial_services", "R40:TONLY_ADX30_TR5_W35_TP15" },
            { "healthcare", "R39:TONLY_ADX27_TR3.0_W30" },
            { "information_technology", "R40:RSI55_ADX30_TR5_W10_TP15" },
            { "metals_and_mining", "R40:PB_ADX20_CD50_TR5_A7_TP10" },
            { "oil_gas_and_consumable_fuels", "R40:RSI55_ADX31_TR3_W22" },
            { "power", "R40:RSI55_ADX30_TR4_W25_TP20" },
            { "realty", "R40:REG_W12_T85_R40_TR5_TP30" },
            { "services", "R41:RSI43_ADX32_TR5_W15_TP8_CS" },
            { "telecommunication", "R40:PB10_ADX18_CD30_TR3_A7" },
            { "textiles", "R38:RSI40_ADX25_TR4_A3" },
    };

    public static void main(String[] args) throws Exception {
        boolean batchMode = args.length > 0 && "--all".equals(args[0]);

        Market market = Market.current();
        LinkedHashMap<String, Supplier<Strategy>> allVariants = VariantRegistry.buildAll();
        allVariants.putAll(RefinedVariants.load(market));  // pattern-search champions
        DataFetcher fetcher = DataFetcher.createDefault();

        if (batchMode) {
            // Per-sector winners come from new_winners.csv (the optimizer's output,
            // single source of truth). Fall back to the hardcoded India array only
            // if that file is missing.
            List<String[]> winners = market.loadWinners();
            if (winners.isEmpty() && !market.isUs()) winners = Arrays.asList(SECTOR_WINNERS);

            System.out.printf("=== Sector Detail Report — ALL WINNERS [market=%s, %d sectors] ===%n",
                    market.code(), winners.size());
            Path outDir = market.reportDir();
            Files.createDirectories(outDir);

            // Consolidated CSVs with Sector & Strategy columns
            try (BufferedWriter perfW = Files.newBufferedWriter(outDir.resolve("per_stock_performance.csv"));
                    BufferedWriter tradeW = Files.newBufferedWriter(outDir.resolve("all_trades.csv"))) {

                perfW.write(
                        "Sector,Strategy,Stock,CAGR,MDD,WR,PF,Trades,Winners,Losers,NetProfit,FinalEquity,Sharpe,AvgBarsHeld");
                perfW.newLine();
                tradeW.write(
                        "Sector,Strategy,Stock,TradeNum,EntryDate,ExitDate,EntryPrice,ExitPrice,PnL,PnL%,MAE%,MFE%,BarsHeld,Comment");
                tradeW.newLine();

                for (String[] sw : winners) {
                    runOneSector(market, sw[0], sw[1], allVariants, fetcher, perfW, tradeW);
                }
            }
            System.out.printf("%nDone — %d sectors processed.%n", winners.size());
            System.out.printf("Consolidated: %s%n", outDir.toAbsolutePath());
        } else {
            String sector = args.length > 0 ? args[0] : "healthcare";
            String stratName = args.length > 1 ? args[1] : "R34:TONLY_ADX33_TR4.5_A8";
            runOneSector(market, sector, stratName, allVariants, fetcher, null, null);
        }
    }

    /**
     * Runs backtest for one sector/strategy. If perfW and tradeW are non-null,
     * appends rows to the consolidated CSVs (batch mode). Otherwise writes
     * per-sector files.
     */
    private static void runOneSector(Market market, String sector, String stratName,
            LinkedHashMap<String, Supplier<Strategy>> allVariants,
            DataFetcher fetcher,
            BufferedWriter perfW, BufferedWriter tradeW) throws Exception {
        System.out.printf("%n══════════════════════════════════════════════%n");
        System.out.printf("  Sector   : %s%n", sector);
        System.out.printf("  Strategy : %s%n", stratName);

        Supplier<Strategy> factory = allVariants.get(stratName);
        if (factory == null) {
            System.err.println("  ERROR: Strategy '" + stratName + "' not found — SKIPPING");
            return;
        }

        // Load stock list (market-aware: classpath then filesystem)
        List<String> stocks = market.loadSectorStocks(sector);
        if (stocks.isEmpty()) {
            System.err.println("  Sector file empty/not found for '" + sector + "' — SKIPPING");
            return;
        }

        // Load bar data
        Map<String, List<Bar>> barsByStock = new LinkedHashMap<>();
        for (String stock : stocks) {
            List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
            if (bars != null && bars.size() > 100)
                barsByStock.put(stock, bars);
        }
        System.out.printf("  Stocks   : %d loaded / %d total%n", barsByStock.size(), stocks.size());

        boolean consolidated = (perfW != null && tradeW != null);

        // ── 1. Per-stock performance (+ cache metrics for summary) ──
        Map<String, Metrics> metricsCache = new LinkedHashMap<>();
        BufferedWriter pw = consolidated ? perfW
                : Files.newBufferedWriter(Files.createDirectories(
                        market.reportDir().resolve(sector)).resolve("per_stock_performance.csv"));
        try {
            if (!consolidated) {
                pw.write("Stock,CAGR,MDD,WR,PF,Trades,Winners,Losers,NetProfit,FinalEquity,Sharpe,AvgBarsHeld");
                pw.newLine();
            }
            for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
                String stock = e.getKey();
                List<Bar> bars = e.getValue();

                Metrics m = runOneWindow(factory, bars, TRADE_FROM, null);
                metricsCache.put(stock, m);
                String prefix = consolidated ? String.format("%s,%s,", sector, stratName) : "";
                if (m == null) {
                    pw.write(String.format("%s%s,0,0,0,0,0,0,0,0,0,0,0", prefix, stock));
                } else {
                    pw.write(String.format(Locale.US,
                            "%s%s,%.2f,%.2f,%.2f,%.4f,%d,%d,%d,%.2f,%.2f,%.4f,%.1f",
                            prefix, stock,
                            cagrForWindow(m, TRADE_FROM, null), m.getMaxDrawdownPct(), m.getWinRate(),
                            m.getProfitFactor(),
                            m.getTotalTrades(), m.getWinningTrades(), m.getLosingTrades(),
                            m.getNetProfit(), m.getFinalEquity(), m.getSharpeRatio(),
                            m.getAvgBarsHeld()));
                }
                pw.newLine();
            }
        } finally {
            if (!consolidated)
                pw.close();
        }

        // ── 2. All trades ────────────────────────────────────────
        BufferedWriter tw = consolidated ? tradeW
                : Files.newBufferedWriter(market.reportDir().resolve(sector).resolve("all_trades.csv"));
        try {
            if (!consolidated) {
                tw.write("Stock,TradeNum,EntryDate,ExitDate,EntryPrice,ExitPrice,PnL,PnL%,MAE%,MFE%,BarsHeld,Comment");
                tw.newLine();
            }
            for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
                String stock = e.getKey();
                List<Bar> bars = e.getValue();

                List<ClosedTrade> trades = runOneWindowTrades(factory, bars, TRADE_FROM, null);
                if (trades == null)
                    continue;

                String prefix = consolidated ? String.format("%s,%s,", sector, stratName) : "";
                int tradeNum = 0;
                for (ClosedTrade t : trades) {
                    if (t.getExitDate() == null)
                        continue;
                    if (t.getExitDate().isBefore(TRADE_FROM))
                        continue;
                    tradeNum++;
                    tw.write(String.format(Locale.US,
                            "%s%s,%d,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%s",
                            prefix, stock, tradeNum,
                            t.getEntryDate(), t.getExitDate(),
                            t.getEntryPrice(), t.getExitPrice(),
                            t.getPnl(), t.getPnlPct(), t.getMaePct(), t.getMfePct(), t.getBarsHeld(),
                            t.getExitComment() != null ? t.getExitComment() : ""));
                    tw.newLine();
                }
            }
        } finally {
            if (!consolidated)
                tw.close();
        }

        // ── 3. Summary to console (from cached metrics) ──────────
        System.out.printf("  %-20s %7s %7s %7s %7s %6s%n",
                "Stock", "CAGR", "MDD", "WR", "PF", "Trades");

        double totalCagr = 0, totalMdd = 0;
        int countStocks = 0;
        for (Map.Entry<String, Metrics> e : metricsCache.entrySet()) {
            Metrics m = e.getValue();
            if (m == null || m.getTotalTrades() == 0)
                continue;
            double c = cagrForWindow(m, TRADE_FROM, null);
            totalCagr += c;
            totalMdd += Math.abs(m.getMaxDrawdownPct());
            countStocks++;
        }
        if (countStocks > 0) {
            System.out.printf(Locale.US, "  AVG (%d stocks): CAGR %.1f%%, MDD %.1f%%%n",
                    countStocks, totalCagr / countStocks, -totalMdd / countStocks);
        }
    }

    private static double cagrForWindow(Metrics m, LocalDate from, LocalDate to) {
        if (m.getInitialCapital() <= 0 || m.getFinalEquity() <= 0)
            return 0;
        double ratio = m.getFinalEquity() / m.getInitialCapital();
        LocalDate end = to != null ? to : LocalDate.now();
        double years = (double) java.time.temporal.ChronoUnit.DAYS.between(from, end) / 365.25;
        if (years <= 0)
            return 0;
        return (Math.pow(ratio, 1.0 / years) - 1) * 100.0;
    }

    private static Metrics runOneWindow(Supplier<Strategy> factory, List<Bar> bars,
            LocalDate tradeFrom, LocalDate tradeTo) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        double cap = BacktestConfig.INITIAL_CAPITAL;
        TradingViewEngine engine = new TradingViewEngine(
                cap, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
                "percent_of_equity", 100.0, 0);

        LocalDate lastDate = null;
        double lastClose = 0;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            if (tradeTo != null && d.isAfter(tradeTo))
                break;
            engine.processBar(d, bar.getOpen(), bar.getHigh(),
                    bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(tradeFrom);
            strategy.onBar(engine, data, i, active);
            lastDate = d;
            lastClose = bar.getClose();
        }
        if (lastDate == null)
            return null;
        engine.finalize(lastDate, lastClose);

        List<EquityPoint> curve = engine.getEquityCurve();
        List<ClosedTrade> trades = engine.getClosedTrades();
        if (curve.size() < 2)
            return null;

        return SubWindowMetrics.slice(curve, trades, tradeFrom, tradeTo, cap);
    }

    private static List<ClosedTrade> runOneWindowTrades(Supplier<Strategy> factory, List<Bar> bars,
            LocalDate tradeFrom, LocalDate tradeTo) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        double cap = BacktestConfig.INITIAL_CAPITAL;
        TradingViewEngine engine = new TradingViewEngine(
                cap, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
                "percent_of_equity", 100.0, 0);

        LocalDate lastDate = null;
        double lastClose = 0;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            if (tradeTo != null && d.isAfter(tradeTo))
                break;
            engine.processBar(d, bar.getOpen(), bar.getHigh(),
                    bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(tradeFrom);
            strategy.onBar(engine, data, i, active);
            lastDate = d;
            lastClose = bar.getClose();
        }
        if (lastDate == null)
            return null;
        engine.finalize(lastDate, lastClose);

        return engine.getClosedTrades();
    }
}
