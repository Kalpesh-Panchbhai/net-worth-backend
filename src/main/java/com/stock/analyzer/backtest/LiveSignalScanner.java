package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.Market;
import com.stock.analyzer.backtest.config.RefinedVariants;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.data.YahooFinanceFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;

import com.stock.analyzer.backtest.strategy.Strategy;
import com.stock.analyzer.backtest.strategy.StrategyData;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;

import java.util.*;
import java.util.function.Supplier;

/**
 * Live Signal Scanner — runs all 18 sector strategies and reports BUY/SELL
 * signals.
 *
 * Modes:
 * LiveSignalScanner — uses cached CSV data
 * LiveSignalScanner --live — fetches live data from Yahoo Finance API
 * LiveSignalScanner --live --json — live + JSON output
 *
 *
 * Signal types:
 * BUY — strategy triggered entry on the last bar
 * SELL — strategy triggered exit on the last bar
 * HOLD — currently in a position, no exit signal
 * FLAT — no position, no entry signal
 */
public class LiveSignalScanner {

    private static final LocalDate TRADE_FROM = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);
    // Live mode only needs 1 year warm-up before trade start (EMA 200 ≈ 250 bars)
    private static final LocalDate LIVE_WARMUP_START = LocalDate.parse("2017-01-01");
    private static final LocalDate CACHE_WARMUP_START = LocalDate.parse(BacktestConfig.WARMUP_START);

    // Synced to sector_all_phases/new_winners.csv after 2026-05-29 data refresh.
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
        Set<String> flags = new HashSet<>(Arrays.asList(args));
        boolean liveMode = flags.contains("--live");
        boolean jsonOutput = flags.contains("--json");

        Market market = Market.current();
        LinkedHashMap<String, Supplier<Strategy>> allVariants = VariantRegistry.buildAll();
        allVariants.putAll(RefinedVariants.load(market));  // pattern-search champions

        // Per-sector winners from new_winners.csv (single source of truth);
        // fall back to the hardcoded India array only if that file is missing.
        List<String[]> sectorWinners = market.loadWinners();
        if (sectorWinners.isEmpty() && !market.isUs()) sectorWinners = Arrays.asList(SECTOR_WINNERS);

        // Data source: live Yahoo API (market timezone) or local cache
        YahooFinanceFetcher yahooFetcher = liveMode ? new YahooFinanceFetcher(market.timezone()) : null;
        DataFetcher cacheFetcher = liveMode ? null : DataFetcher.createDefault();

        List<Signal> allSignals = new ArrayList<>();
        int totalStocks = 0;
        int fetchErrors = 0;
        int stocksAttempted = 0;
        List<String> failedStocks = new ArrayList<>();

        // Count total expected stocks for progress reporting
        int totalExpected = 0;
        for (String[] sw2 : sectorWinners) {
            totalExpected += market.loadSectorStocks(sw2[0]).size();
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  LIVE SIGNAL SCANNER — " + LocalDate.now());
        System.out.println("  Market: " + market.code() + "  |  Sectors: " + sectorWinners.size());
        System.out.println("  Mode: " + (liveMode ? "LIVE (Yahoo Finance API)" : "CACHED (local CSV)"));
        System.out.println("═══════════════════════════════════════════════════════════════");

        for (String[] sw : sectorWinners) {
            String sector = sw[0];
            String stratName = sw[1];

            Supplier<Strategy> factory = allVariants.get(stratName);
            if (factory == null) {
                System.err.println("  ⚠ Strategy not found: " + stratName);
                continue;
            }

            List<String> stocks = market.loadSectorStocks(sector);
            if (stocks.isEmpty())
                continue;

            System.out.printf("  Scanning %s (%d stocks)...%n", sector, stocks.size());

            for (int si = 0; si < stocks.size(); si++) {
                String stock = stocks.get(si);
                stocksAttempted++;
                System.out.printf("  [%d/%d] %s — %s%n", stocksAttempted, totalExpected, stock, sector);
                List<Bar> bars;
                if (liveMode) {
                    bars = yahooFetcher.fetchDaily(stock, LIVE_WARMUP_START);
                } else {
                    bars = cacheFetcher.fetchData(stock, CACHE_WARMUP_START);
                }

                if (bars == null || bars.size() < 100) {
                    fetchErrors++;
                    String reason = bars == null ? "no data" : "insufficient bars (" + bars.size() + ")";
                    failedStocks.add(stock.replace(".NS", "") + " (" + reason + ")");
                    System.out.printf("  FETCH_ERROR %s — %s — %s%n", stock, sector, reason);
                    continue;
                }
                totalStocks++;

                Signal signal = scanOneStock(factory, bars, stock, sector, stratName);
                if (signal != null) {
                    allSignals.add(signal);
                }
            }
        }

        // Separate signals by type
        List<Signal> buys = new ArrayList<>();
        List<Signal> sells = new ArrayList<>();
        List<Signal> holds = new ArrayList<>();

        for (Signal s : allSignals) {
            switch (s.type) {
                case "BUY":
                    buys.add(s);
                    break;
                case "SELL":
                    sells.add(s);
                    break;
                case "HOLD":
                    holds.add(s);
                    break;
            }
        }

        // Print signals to console
        System.out.printf("%n  Scanned: %d stocks across %d sectors", totalStocks, sectorWinners.size());
        if (fetchErrors > 0) {
            System.out.printf(" (%d fetch errors)", fetchErrors);
            System.out.println();
            System.out.println("  Failed stocks:");
            for (String f : failedStocks)
                System.out.println("    ✗ " + f);
        }
        System.out.printf("%n  Date: %s%n%n", allSignals.isEmpty() ? LocalDate.now() : allSignals.get(0).dataDate);

        if (!buys.isEmpty()) {
            System.out.println("  🟢 BUY SIGNALS (" + buys.size() + ")");
            System.out.println("  ─────────────────────────────────────────────────────────");
            printSignalTable(buys);
        }

        if (!sells.isEmpty()) {
            System.out.println("\n  🔴 SELL SIGNALS (" + sells.size() + ")");
            System.out.println("  ─────────────────────────────────────────────────────────");
            printSignalTable(sells);
        }

        System.out.printf("%n  Summary: %d BUY | %d SELL | %d HOLD | %d FLAT%n",
                buys.size(), sells.size(), holds.size(),
                totalStocks - buys.size() - sells.size() - holds.size());

        // ── Write CSV (only if running locally with file access) ──────
        try {
            Path outDir = market.reportDir();
            Files.createDirectories(outDir);

            Path csvPath = outDir.resolve("signals.csv");
            try (BufferedWriter w = Files.newBufferedWriter(csvPath)) {
                w.write("Date,Sector,Strategy,Stock,Signal,Price,StopLoss,TakeProfit,EntryPrice,EntryDate,UnrealizedPnlPct,Actionable,Comment");
                w.newLine();
                for (Signal s : allSignals) {
                    if ("FLAT".equals(s.type))
                        continue;
                    w.write(String.format(Locale.US, "%s,%s,%s,%s,%s,%.2f,%s,%s,%s,%s,%s,%s,%s",
                            s.dataDate, s.sector, s.strategy, s.stock, s.type,
                            s.price,
                            s.stopLoss != null ? String.format(Locale.US, "%.2f", s.stopLoss) : "",
                            s.takeProfit != null ? String.format(Locale.US, "%.2f", s.takeProfit) : "",
                            s.entryPrice != null ? String.format(Locale.US, "%.2f", s.entryPrice) : "",
                            s.entryDate != null ? s.entryDate.toString() : "",
                            s.unrealizedPnlPct != null ? String.format(Locale.US, "%.2f", s.unrealizedPnlPct) : "",
                            s.actionableTomorrow ? "true" : "false",
                            s.comment != null ? s.comment : ""));
                    w.newLine();
                }
            }
            System.out.printf("  CSV saved: %s%n", csvPath);

            if (jsonOutput) {
                Path jsonPath = outDir.resolve("signals.json");
                try (BufferedWriter w = Files.newBufferedWriter(jsonPath)) {
                    w.write("[\n");
                    boolean first = true;
                    for (Signal s : allSignals) {
                        if ("FLAT".equals(s.type))
                            continue;
                        if (!first)
                            w.write(",\n");
                        first = false;
                        w.write(String.format(Locale.US,
                                "  {\"date\":\"%s\",\"sector\":\"%s\",\"strategy\":\"%s\"," +
                                        "\"stock\":\"%s\",\"signal\":\"%s\",\"price\":%.2f" +
                                        "%s%s%s}",
                                s.dataDate, s.sector, s.strategy, s.stock, s.type, s.price,
                                s.stopLoss != null ? String.format(Locale.US, ",\"stop_loss\":%.2f", s.stopLoss) : "",
                                s.takeProfit != null ? String.format(Locale.US, ",\"take_profit\":%.2f", s.takeProfit)
                                        : "",
                                s.comment != null ? String.format(",\"comment\":\"%s\"", s.comment) : ""));
                    }
                    w.write("\n]\n");
                }
                System.out.printf("  JSON saved: %s%n", jsonPath);
            }
        } catch (Exception e) {
            System.out.println("  (Skipping file output — " + e.getMessage() + ")");
        }

        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    /**
     * Run strategy on one stock and detect the signal on the last bar.
     * We compare position state before and after the last bar to detect
     * transitions.
     */
    private static Signal scanOneStock(Supplier<Strategy> factory, List<Bar> bars,
            String stock, String sector, String stratName) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        double cap = BacktestConfig.INITIAL_CAPITAL;
        TradingViewEngine engine = new TradingViewEngine(
                cap, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
                "percent_of_equity", 100.0, 0);

        // Run all bars except the last one
        int lastIdx = bars.size() - 1;
        for (int i = 0; i < lastIdx; i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            engine.processBar(d, bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(TRADE_FROM);
            strategy.onBar(engine, data, i, active);
        }

        // Capture state before last bar
        boolean hadPosition = engine.getPosition() != null;
        int tradesBeforeLast = engine.getClosedTrades().size();

        // Process last bar
        Bar lastBar = bars.get(lastIdx);
        LocalDate lastDate = lastBar.getDate();
        engine.processBar(lastDate, lastBar.getOpen(), lastBar.getHigh(),
                lastBar.getLow(), lastBar.getClose(), lastIdx);
        boolean active = !lastDate.isBefore(TRADE_FROM);
        strategy.onBar(engine, data, lastIdx, active);

        // Detect signal from state change
        boolean hasPosition = engine.getPosition() != null;
        int tradesAfterLast = engine.getClosedTrades().size();
        boolean tradeClosedOnLastBar = tradesAfterLast > tradesBeforeLast;

        String signalType;
        double price = lastBar.getClose();
        Double stopLoss = null;
        Double takeProfit = null;
        Double entryPrice = null;
        Double unrealizedPnlPct = null;
        LocalDate entryDate = null;
        String comment = null;

        // Pending orders queued during last bar's strategy.onBar — fill NEXT trading
        // day's open.
        // Critical: SL/TP detected on last bar close + strategy-signal exits BOTH queue
        // here.
        boolean pendingExit = engine.hasPendingExit();
        boolean pendingEntry = engine.hasPendingEntry();
        boolean actionableTomorrow = false;

        if (hadPosition && hasPosition && pendingExit) {
            // Exit signal fired on last bar close → execute SELL at next trading day open
            signalType = "SELL";
            actionableTomorrow = true;
            Position pos = engine.getPosition();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            String exitReason = engine.getPendingExitComment();
            comment = "Execute next session open. Reason: " + (exitReason != null ? exitReason : "Exit");
            // price stays = lastBar.close (reference only; actual fill = next-day open)
        } else if (!hadPosition && !hasPosition && pendingEntry) {
            // Entry signal fired on last bar close → execute BUY at next trading day open
            signalType = "BUY";
            actionableTomorrow = true;
            comment = "Execute next session open";
            // price stays = lastBar.close (reference only; actual fill = next-day open)
        } else if (!hadPosition && hasPosition) {
            // Entry from PREVIOUS bar's signal already filled at this bar's open
            signalType = "BUY";
            Position pos = engine.getPosition();
            price = pos.getEntryPrice();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            comment = "Already filled at session open. " + (pos.getComment() != null ? pos.getComment() : "");
        } else if (hadPosition && !hasPosition) {
            // Exit from PREVIOUS bar's signal already filled at this bar's open
            signalType = "SELL";
            if (tradesAfterLast > tradesBeforeLast) {
                ClosedTrade lastTrade = engine.getClosedTrades().get(tradesAfterLast - 1);
                price = lastTrade.getExitPrice();
                comment = "Already filled at session open. Reason: " + lastTrade.getExitComment();
            }
        } else if (hadPosition && hasPosition && tradeClosedOnLastBar) {
            // Reversal: previous exit filled today + new entry queued from today's close
            signalType = "BUY";
            actionableTomorrow = pendingEntry;
            Position pos = engine.getPosition();
            price = pos.getEntryPrice();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            comment = "Reversed. " + (pos.getComment() != null ? pos.getComment() : "");
        } else if (hasPosition) {
            signalType = "HOLD";
            Position pos = engine.getPosition();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            entryPrice = pos.getEntryPrice();
            entryDate = pos.getEntryDate();
            unrealizedPnlPct = ((price - entryPrice) / entryPrice) * 100.0;
        } else {
            signalType = "FLAT";
        }

        return new Signal(lastDate, sector, stratName, stock, signalType,
                price, stopLoss, takeProfit, entryPrice, entryDate, unrealizedPnlPct,
                comment, actionableTomorrow);
    }

    private static void printSignalTable(List<Signal> signals) {
        System.out.printf("  %-35s %-18s %10s %10s %10s  %s%n",
                "Sector", "Stock", "Price", "SL", "TP", "Comment");
        for (Signal s : signals) {
            System.out.printf("  %-35s %-18s %10.2f %10s %10s  %s%n",
                    s.sector, s.stock, s.price,
                    s.stopLoss != null ? String.format("%.2f", s.stopLoss) : "—",
                    s.takeProfit != null ? String.format("%.2f", s.takeProfit) : "—",
                    s.comment != null ? s.comment : "");
        }
    }

    private static List<String> loadSectorStocks(String sector) {
        String sectorFile = "sectors/" + sector + ".txt";
        List<String> stocks = new ArrayList<>();
        try (InputStream is = LiveSignalScanner.class.getClassLoader().getResourceAsStream(sectorFile)) {
            if (is == null)
                return stocks;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty())
                        stocks.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("  ⚠ Error reading " + sectorFile + ": " + e.getMessage());
        }
        return stocks;
    }

    // ── Signal record ───────────────────────────────────────────────

    private static class Signal {
        final LocalDate dataDate;
        final String sector;
        final String strategy;
        final String stock;
        final String type; // BUY, SELL, HOLD, FLAT
        final double price;
        final Double stopLoss;
        final Double takeProfit;
        final Double entryPrice;
        final LocalDate entryDate;
        final Double unrealizedPnlPct;
        final String comment;
        final boolean actionableTomorrow; // true = signal fired on last bar close, fills next session open

        Signal(LocalDate dataDate, String sector, String strategy, String stock,
                String type, double price, Double stopLoss, Double takeProfit,
                Double entryPrice, LocalDate entryDate, Double unrealizedPnlPct,
                String comment, boolean actionableTomorrow) {
            this.dataDate = dataDate;
            this.sector = sector;
            this.strategy = strategy;
            this.stock = stock;
            this.type = type;
            this.price = price;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.entryPrice = entryPrice;
            this.entryDate = entryDate;
            this.unrealizedPnlPct = unrealizedPnlPct;
            this.comment = comment;
            this.actionableTomorrow = actionableTomorrow;
        }
    }
}
