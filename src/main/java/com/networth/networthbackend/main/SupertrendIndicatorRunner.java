package com.networth.networthbackend.main;

import com.networth.networthbackend.strategy.SupertrendService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SupertrendIndicatorRunner {

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        System.out.println(LocalDateTime.now());
//        new SupertrendService().execute(Boolean.valueOf(args[0]), Boolean.valueOf(args[1]));
        SupertrendService supertrendService = new SupertrendService();
        List<String> symbols = supertrendService.readSymbols();
        double total = 0.0;
        int count = 0;
        List<StockDayResult> results = new ArrayList<>();
        System.out.printf("%-5s %-20s %20s %12s %12s %12s %16s\n", "#", "Symbol", "Per Day Profit (%)", "Total Days", "#Trades", "TradeDays", "AvgTradeDays");
        System.out.println("---------------------------------------------------------------------------------------------------------------");
        for (String symbol : symbols) {
            SupertrendService.SupertrendTradeStats stats = supertrendService.simulateSupertrendTrades(symbol);
            List<SupertrendService.Trade> trades = stats.trades;
            SupertrendService.PerDayResult perDayResult = SupertrendService.calculatePerDayPercent(trades);
            System.out.printf("%-5d %-20s %20.4f %12d %12d %12d %16.2f\n", count + 1, symbol, perDayResult.perDay, perDayResult.totalDays, stats.totalTrades, stats.totalTradeDays, stats.avgTradeDays);
            total += perDayResult.perDay;
            results.add(new StockDayResult(symbol, perDayResult.perDay, perDayResult.totalDays, stats.totalTrades, stats.totalTradeDays, stats.avgTradeDays));
            count++;
        }
        if (count > 0) {
            double avg = total / count;
            System.out.println("Average Per Day Profit (all stocks): " + avg + "%");
        }
        results = results.stream().sorted((o1, o2) -> Double.compare(o2.perDay, o1.perDay)).toList();
        int rank = 1;
        int totalTradesAllStocks = 0;
        int totalTradeDaysAllStocks = 0;
        for (StockDayResult r : results) {
            totalTradesAllStocks += r.totalTrades;
            totalTradeDaysAllStocks += r.tradeDays;
        }
        double combinedAvgTradeDays = totalTradesAllStocks > 0 ? (double) totalTradeDaysAllStocks / totalTradesAllStocks : 0.0;
        System.out.printf("Combined average trade days for all completed trades across all stocks: %.2f\n", combinedAvgTradeDays);
        try (java.io.PrintWriter pw = new java.io.PrintWriter("supertrend_per_day_results.csv")) {
            pw.println("Rank,Symbol,PerDayProfit,TotalDays,TotalTrades,TradeDays,AvgTradeDays");
            for (StockDayResult r : results) {
                pw.printf("%d,%s,%.6f,%d,%d,%d,%.2f\n", rank, r.symbol, r.perDay, r.totalDays, r.totalTrades, r.tradeDays, r.avgTradeDays);
                rank++;
            }
            System.out.println("\nCSV export complete: supertrend_per_day_results.csv");
        } catch (Exception e) {
            System.err.println("Error writing CSV: " + e.getMessage());
        }
        System.out.println(LocalDateTime.now());
    }

    static class StockDayResult {
        String symbol;
        double perDay;
        long totalDays;
        int totalTrades;
        int tradeDays;
        double avgTradeDays;

        StockDayResult(String symbol, double perDay, long totalDays, int totalTrades, int tradeDays, double avgTradeDays) {
            this.symbol = symbol;
            this.perDay = perDay;
            this.totalDays = totalDays;
            this.totalTrades = totalTrades;
            this.tradeDays = tradeDays;
            this.avgTradeDays = avgTradeDays;
        }
    }
}
