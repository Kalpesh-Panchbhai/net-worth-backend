package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.strategy.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Runs each sector's winning strategy on its own sector stocks and exports
 * every trade detail to a per-sector CSV file.
 *
 * Output: sector_csv/<sector>_trades.csv with columns:
 * Stock, TradeNum, EntryDate, EntryPrice, ExitDate, ExitPrice, PnL, PnL%,
 * BarsHeld, EntryComment, ExitComment
 */
public class SectorTradeDetailRunner {

    // Synced with LiveSignalScanner.SECTOR_WINNERS
    private static final String[][] SECTORS = {
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
        Path outDir = Paths.get("sector_csv");
        Files.createDirectories(outDir);

        // Build all strategies once and index by name
        LinkedHashMap<String, Supplier<Strategy>> allStrategies = VariantRegistry.buildAll();

        DataFetcher fetcher = DataFetcher.createDefault();
        LocalDate tradeFromDt = LocalDate.parse(BacktestConfig.TRADE_FROM);

        // Combined CSV for all sectors
        BufferedWriter combinedOut = Files.newBufferedWriter(outDir.resolve("ALL_SECTORS_trades.csv"));
        combinedOut.write(
                "Sector,Strategy,Stock,TradeNum,EntryDate,EntryPrice,ExitDate,ExitPrice,PnL,PnL%,MAE%,MFE%,BarsHeld,EntryComment,ExitComment");
        combinedOut.newLine();

        int totalTrades = 0;

        for (String[] sectorInfo : SECTORS) {
            String sectorName = sectorInfo[0];
            String stratName = sectorInfo[1];

            Supplier<Strategy> factory = allStrategies.get(stratName);
            if (factory == null) {
                System.err.printf("  ✗ Strategy not found: %s%n", stratName);
                continue;
            }

            // Load sector stocks
            Path sectorFile = Paths.get("src/main/resources/sectors/" + sectorName + ".txt");
            if (!Files.exists(sectorFile)) {
                System.err.printf("  ✗ Sector file missing: %s%n", sectorFile);
                continue;
            }
            List<String> symbols = Files.readAllLines(sectorFile);
            symbols.removeIf(s -> s == null || s.trim().isEmpty());

            // Per-sector CSV
            String csvPath = "sector_csv/" + sectorName + "_trades.csv";
            BufferedWriter out = Files.newBufferedWriter(Paths.get(csvPath));
            out.write(
                    "Stock,TradeNum,EntryDate,EntryPrice,ExitDate,ExitPrice,PnL,PnL%,MAE%,MFE%,BarsHeld,EntryComment,ExitComment");
            out.newLine();

            int sectorTrades = 0;
            for (String symbol : symbols) {
                Strategy strategy = factory.get();

                List<Bar> bars = fetcher.fetchData(symbol, BacktestConfig.WARMUP_START);
                if (bars == null || bars.size() < 50)
                    continue;

                StrategyData data = strategy.computeIndicators(bars);

                TradingViewEngine engine = new TradingViewEngine(
                        BacktestConfig.INITIAL_CAPITAL,
                        BacktestConfig.COMMISSION_PCT,
                        BacktestConfig.SLIPPAGE,
                        "percent_of_equity",
                        100.0,
                        0);

                int n = bars.size();
                for (int i = 0; i < n; i++) {
                    Bar bar = bars.get(i);
                    engine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
                    boolean tradeActive = !bar.getDate().isBefore(tradeFromDt);
                    strategy.onBar(engine, data, i, tradeActive);
                }

                Bar lastBar = bars.get(n - 1);
                engine.finalize(lastBar.getDate(), lastBar.getClose());

                List<ClosedTrade> trades = engine.getClosedTrades();
                for (int t = 0; t < trades.size(); t++) {
                    ClosedTrade ct = trades.get(t);
                    String row = String.format(Locale.US,
                            "%s,%d,%s,%.2f,%s,%.2f,%.2f,%.4f,%.4f,%.4f,%d,%s,%s",
                            symbol,
                            t + 1,
                            ct.getEntryDate(),
                            ct.getEntryPrice(),
                            ct.getExitDate(),
                            ct.getExitPrice(),
                            ct.getPnl(),
                            ct.getPnlPct(),
                            ct.getMaePct(),
                            ct.getMfePct(),
                            ct.getBarsHeld(),
                            ct.getEntryComment().replace(",", " "),
                            ct.getExitComment().replace(",", " "));

                    out.write(row);
                    out.newLine();

                    combinedOut.write(String.format("%s,%s,%s", sectorName, stratName, row));
                    combinedOut.newLine();

                    sectorTrades++;
                }
            }
            out.close();
            totalTrades += sectorTrades;
            System.out.printf("  ✓ %-32s → %s (%d trades)%n", sectorName + " (" + stratName + ")", csvPath,
                    sectorTrades);
        }
        combinedOut.close();
        System.out.printf("%n  Combined CSV: sector_csv/ALL_SECTORS_trades.csv (%d total trades)%n", totalTrades);
    }
}
