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
        System.out.printf("%-5s %-20s %20s %12s\n", "#", "Symbol", "Per Day Profit (%)", "Total Days");
        System.out.println("--------------------------------------------------------------------------");
        for (String symbol : symbols) {
            List<SupertrendService.Trade> trades = supertrendService.simulateSupertrendTrades(symbol);
            SupertrendService.PerDayResult perDayResult = SupertrendService.calculatePerDayPercent(trades);
            System.out.printf("%-5d %-20s %20.4f %12d\n", count + 1, symbol, perDayResult.perDay, perDayResult.totalDays);
            total += perDayResult.perDay;
            results.add(new StockDayResult(symbol, perDayResult.perDay, perDayResult.totalDays));
            count++;
        }
        if (count > 0) {
            double avg = total / count;
            System.out.println("Average Per Day Profit (all stocks): " + avg + "%");
        }
        results = results.stream().sorted((o1, o2) -> Double.compare(o2.perDay, o1.perDay)).toList();
        int rank = 1;
        try (java.io.PrintWriter pw = new java.io.PrintWriter("supertrend_per_day_results.csv")) {
            pw.println("Rank,Symbol,PerDayProfit,TotalDays");
            for (StockDayResult r : results) {
                pw.printf("%d,%s,%.6f,%d\n", rank, r.symbol, r.perDay, r.totalDays);
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

        StockDayResult(String symbol, double perDay, long totalDays) {
            this.symbol = symbol;
            this.perDay = perDay;
            this.totalDays = totalDays;
        }
    }
}
