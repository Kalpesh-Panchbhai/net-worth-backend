package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
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
 * Portfolio comparison: OLD (deployed) vs NEW (R40/R39) strategies.
 * Invests ₹1,000 in each stock per sector using each strategy,
 * then sums up to get sector-level and total portfolio returns.
 */
public class SectorPortfolioCompare {

    private static final double INVEST_PER_STOCK = 1000.0;
    private static final LocalDate TRADE_FROM = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);

    // OLD = currently deployed in LiveSignalScanner
    private static final String[][] OLD_STRATEGIES = {
        { "automobile_and_auto_components", "R34:TONLY_ADX32_TR4.0_A6" },
        { "capital_goods",                 "HA_R14:PB_TONLY_TR3_A2" },
        { "chemicals",                     "R34:TONLY_ADX31_TR4.0_A5" },
        { "construction",                  "R22:HA_RSIF45_TR0.48" },
        { "construction_materials",        "R34:TONLY_ADX33_TR4.5_A5" },
        { "consumer_durables",             "R34:TONLY_ADX27_TR5.0_A6" },
        { "consumer_services",             "R34:TONLY_ADX33_TR4.5_A5" },
        { "fast_moving_consumer_goods",    "R34:TONLY_ADX31_TR4.0_A8" },
        { "financial_services",            "R34:TONLY_ADX32_TR4.0_A8" },
        { "healthcare",                    "R34:TONLY_ADX33_TR4.5_A8" },
        { "information_technology",        "R34:TONLY_ADX28_TR5.0_A8" },
        { "metals_and_mining",             "HA_R14:PB_TONLY_TR3_A2" },
        { "oil_gas_and_consumable_fuels",  "R34:TONLY_ADX27_TR5.5_A8" },
        { "realty",                        "R21:RSI_HEB2_VOL8" },
        { "services",                      "HA_R30:REG_TP3" },
        { "textiles",                      "HA_R31:RSI20_SL15_TP10" },
    };

    // NEW = best from R40/R39 optimization
    private static final String[][] NEW_STRATEGIES = {
        { "automobile_and_auto_components", "R40:TONLY_ADX29_TR5_W20" },
        { "capital_goods",                 "R40:PB_ADX13_CD20_TR5_A5_TP30" },
        { "chemicals",                     "R40:TONLY_ADX27_TR4_W30_TP10" },
        { "construction",                  "R40:RSI50_ADX25_TR3_W25_TP15" },
        { "construction_materials",        "R40:TONLY_ADX33_TR4_W15_TP15" },
        { "consumer_durables",             "R40:TONLY_ADX30_TR5_W15_TP25" },
        { "consumer_services",             "R40:RSI45_ADX30_TR5_W10_TP10" },
        { "fast_moving_consumer_goods",    "R40:TONLY_ADX27_TR4_W25_TP15" },
        { "financial_services",            "R40:TONLY_ADX30_TR5_W35_TP15" },
        { "healthcare",                    "R40:TONLY_ADX27_TR5_W30_TP20" },
        { "information_technology",        "R40:TONLY_ADX29_CD25_TR5_A12" },
        { "metals_and_mining",             "R40:PB_ADX20_CD50_TR5_A7_TP10" },
        { "oil_gas_and_consumable_fuels",  "R40:RSI55_ADX31_TR3_W22" },
        { "power",                         "R40:PB_ADX20_CD50_TR3_A5_TP10" },
        { "realty",                        "R40:REG_W12_T85_R40_TR5_TP30" },
        { "services",                      "R40:RSI45_ADX30_TR4_W10_TP10" },
        { "telecommunication",             "R40:PB12_ADX20_TR3_A7_TP10" },
        { "textiles",                      "R39:RSI50_ADX25_TR4_TP15" },
    };

    public static void main(String[] args) throws Exception {
        LinkedHashMap<String, Supplier<Strategy>> allVariants = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();

        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  SECTOR PORTFOLIO COMPARE — ₹%.0f per stock   |   Trade from: %s%n", INVEST_PER_STOCK, TRADE_FROM);
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");

        // Collect all unique sectors
        Set<String> allSectors = new LinkedHashSet<>();
        for (String[] s : OLD_STRATEGIES) allSectors.add(s[0]);
        for (String[] s : NEW_STRATEGIES) allSectors.add(s[0]);

        // Build maps
        Map<String, String> oldMap = new LinkedHashMap<>();
        for (String[] s : OLD_STRATEGIES) oldMap.put(s[0], s[1]);
        Map<String, String> newMap = new LinkedHashMap<>();
        for (String[] s : NEW_STRATEGIES) newMap.put(s[0], s[1]);

        double grandOldInvested = 0, grandOldFinal = 0;
        double grandNewInvested = 0, grandNewFinal = 0;
        int grandOldStocks = 0, grandNewStocks = 0;

        // CSV output
        Path outDir = Paths.get("sector_portfolio_compare");
        Files.createDirectories(outDir);
        BufferedWriter csv = Files.newBufferedWriter(outDir.resolve("portfolio_compare.csv"));
        csv.write("Sector,Stocks,Old_Strategy,Old_Invested,Old_FinalValue,Old_Profit,Old_Return%,New_Strategy,New_Invested,New_FinalValue,New_Profit,New_Return%");
        csv.newLine();

        System.out.printf("%n  %-30s %5s | %-14s %10s %10s %8s | %-14s %10s %10s %8s%n",
            "Sector", "Stks", "OLD Strategy", "Invested", "Final", "Return%",
                                              "NEW Strategy", "Invested", "Final", "Return%");
        System.out.println("  " + "-".repeat(140));

        for (String sector : allSectors) {
            Path sectorFile = Paths.get("src/main/resources/sectors/" + sector + ".txt");
            if (!Files.exists(sectorFile)) {
                System.err.printf("  [%s] sector file missing — skipping%n", sector);
                continue;
            }
            List<String> stocks = Files.readAllLines(sectorFile);
            stocks.removeIf(s -> s == null || s.trim().isEmpty());

            // Pre-load bars
            LinkedHashMap<String, List<Bar>> barsByStock = new LinkedHashMap<>();
            for (String stock : stocks) {
                List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
                if (bars != null && bars.size() >= 100) barsByStock.put(stock, bars);
            }

            int numStocks = barsByStock.size();

            // OLD strategy
            String oldStrat = oldMap.get(sector);
            double oldInvested = 0, oldFinal = 0;
            if (oldStrat != null) {
                Supplier<Strategy> oldFactory = allVariants.get(oldStrat);
                if (oldFactory != null) {
                    for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
                        double eq = runAndGetFinalEquity(oldFactory, e.getValue(), INVEST_PER_STOCK);
                        oldInvested += INVEST_PER_STOCK;
                        oldFinal += eq;
                    }
                }
            }

            // NEW strategy
            String newStrat = newMap.get(sector);
            double newInvested = 0, newFinal = 0;
            if (newStrat != null) {
                Supplier<Strategy> newFactory = allVariants.get(newStrat);
                if (newFactory != null) {
                    for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
                        double eq = runAndGetFinalEquity(newFactory, e.getValue(), INVEST_PER_STOCK);
                        newInvested += INVEST_PER_STOCK;
                        newFinal += eq;
                    }
                }
            }

            double oldReturn = oldInvested > 0 ? ((oldFinal - oldInvested) / oldInvested) * 100 : 0;
            double newReturn = newInvested > 0 ? ((newFinal - newInvested) / newInvested) * 100 : 0;
            double oldProfit = oldFinal - oldInvested;
            double newProfit = newFinal - newInvested;

            String oldLabel = oldStrat != null ? truncate(oldStrat, 14) : "(none)";
            String newLabel = newStrat != null ? truncate(newStrat, 14) : "(none)";

            System.out.printf("  %-30s %5d | %-14s %10s %10s %7.1f%% | %-14s %10s %10s %7.1f%%%n",
                sector, numStocks,
                oldLabel, fmt(oldInvested), fmt(oldFinal), oldReturn,
                newLabel, fmt(newInvested), fmt(newFinal), newReturn);

            csv.write(String.format(Locale.US, "%s,%d,%s,%.0f,%.0f,%.0f,%.1f,%s,%.0f,%.0f,%.0f,%.1f",
                sector, numStocks,
                oldStrat != null ? oldStrat : "", oldInvested, oldFinal, oldProfit, oldReturn,
                newStrat != null ? newStrat : "", newInvested, newFinal, newProfit, newReturn));
            csv.newLine();

            grandOldInvested += oldInvested;
            grandOldFinal += oldFinal;
            grandNewInvested += newInvested;
            grandNewFinal += newFinal;
            grandOldStocks += (oldStrat != null ? numStocks : 0);
            grandNewStocks += numStocks;
        }

        System.out.println("  " + "-".repeat(140));

        double grandOldReturn = grandOldInvested > 0 ? ((grandOldFinal - grandOldInvested) / grandOldInvested) * 100 : 0;
        double grandNewReturn = grandNewInvested > 0 ? ((grandNewFinal - grandNewInvested) / grandNewInvested) * 100 : 0;

        System.out.printf("  %-30s %5s | %-14s %10s %10s %7.1f%% | %-14s %10s %10s %7.1f%%%n",
            "TOTAL PORTFOLIO", "",
            "OLD", fmt(grandOldInvested), fmt(grandOldFinal), grandOldReturn,
            "NEW", fmt(grandNewInvested), fmt(grandNewFinal), grandNewReturn);

        System.out.printf("%n  OLD: ₹%s invested → ₹%s final (₹%s profit, %.1f%% return)%n",
            fmt(grandOldInvested), fmt(grandOldFinal), fmt(grandOldFinal - grandOldInvested), grandOldReturn);
        System.out.printf("  NEW: ₹%s invested → ₹%s final (₹%s profit, %.1f%% return)%n",
            fmt(grandNewInvested), fmt(grandNewFinal), fmt(grandNewFinal - grandNewInvested), grandNewReturn);
        System.out.printf("  Stocks: OLD=%d  NEW=%d%n", grandOldStocks, grandNewStocks);

        csv.write(String.format(Locale.US, "TOTAL,,%s,%.0f,%.0f,%.0f,%.1f,%s,%.0f,%.0f,%.0f,%.1f",
            "OLD_TOTAL", grandOldInvested, grandOldFinal, grandOldFinal - grandOldInvested, grandOldReturn,
            "NEW_TOTAL", grandNewInvested, grandNewFinal, grandNewFinal - grandNewInvested, grandNewReturn));
        csv.newLine();
        csv.close();

        System.out.printf("%n  CSV: %s%n", outDir.resolve("portfolio_compare.csv").toAbsolutePath());
    }

    private static double runAndGetFinalEquity(Supplier<Strategy> factory, List<Bar> bars, double capital) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        TradingViewEngine engine = new TradingViewEngine(
            capital, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
            "percent_of_equity", 100.0, 0);

        LocalDate lastDate = null;
        double lastClose = 0;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            engine.processBar(d, bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(TRADE_FROM);
            strategy.onBar(engine, data, i, active);
            lastDate = d;
            lastClose = bar.getClose();
        }
        if (lastDate == null) return capital;
        engine.finalize(lastDate, lastClose);
        return engine.getEquity();
    }

    private static String fmt(double v) {
        if (v >= 1_00_00_000) return String.format("%.1fCr", v / 1_00_00_000);
        if (v >= 1_00_000) return String.format("%.1fL", v / 1_00_000);
        if (v >= 1_000) return String.format("%.0fK", v / 1_000);
        return String.format("%.0f", v);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
