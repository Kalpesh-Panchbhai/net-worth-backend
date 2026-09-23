package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.model.Asset;
import com.stock.analyzer.backtest.model.StrategyResult;
import com.stock.analyzer.backtest.runner.BacktestRunner;

import java.util.List;

/**
 * Entry point for the Java backtesting framework.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.stock.analyzer.backtest.BacktestMain"
 *   mvn exec:java -Dexec.mainClass="com.stock.analyzer.backtest.BacktestMain" -Dexec.args="smc_bos"
 *   mvn exec:java -Dexec.mainClass="com.stock.analyzer.backtest.BacktestMain" -Dexec.args="--all-stocks"
 *   mvn exec:java -Dexec.mainClass="com.stock.analyzer.backtest.BacktestMain" -Dexec.args="--compare"
 */
public class BacktestMain {

    public static void main(String[] args) {
        String filter = null;
        boolean allStocks = false;
        boolean compare = false;

        for (String arg : args) {
            if ("--all-stocks".equals(arg)) allStocks = true;
            else if ("--compare".equals(arg)) compare = true;
            else filter = arg;
        }

        if (compare) {
            runCompare(filter);
        } else if (allStocks) {
            runWithAllStocks(filter);
        } else {
            runDefault(filter);
        }
    }

    private static void runDefault(String filter) {
        System.out.println("\n  MODE: 50 curated stocks (default)");
        BacktestRunner runner = new BacktestRunner(filter);
        runner.setRankingsFile("rankings_52.xlsx");
        List<StrategyResult> results = runner.run();
        finishRun(runner, results);
    }

    private static void runWithAllStocks(String filter) {
        List<Asset> allAssets = BacktestConfig.loadAllStocks();
        System.out.printf("\n  MODE: All stocks from nifty500.txt (%d stocks with cached data)%n", allAssets.size());
        BacktestRunner runner = new BacktestRunner(filter, allAssets);
        runner.setRankingsFile("rankings_all.xlsx");
        List<StrategyResult> results = runner.run();
        finishRun(runner, results);
    }

    private static void runCompare(String filter) {
        // Run on 52 stocks
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  PHASE 1: Running on 50 curated stocks");
        System.out.println("=".repeat(80));
        BacktestRunner runner52 = new BacktestRunner(filter);
        runner52.setRankingsFile("rankings_52.xlsx");
        List<StrategyResult> results52 = runner52.run();
        finishRun(runner52, results52);

        // Run on all stocks
        List<Asset> allAssets = BacktestConfig.loadAllStocks();
        System.out.println("\n" + "=".repeat(80));
        System.out.printf("  PHASE 2: Running on %d stocks from nifty500.txt%n", allAssets.size());
        System.out.println("=".repeat(80));
        BacktestRunner runnerAll = new BacktestRunner(filter, allAssets);
        runnerAll.setRankingsFile("rankings_all.xlsx");
        List<StrategyResult> resultsAll = runnerAll.run();
        finishRun(runnerAll, resultsAll);

        // Print comparison
        printComparison(results52, resultsAll);
    }

    private static void finishRun(BacktestRunner runner, List<StrategyResult> results) {
        if (!results.isEmpty()) {
            runner.printRankings(results);
            try {
                runner.writeRankings(results);
            } catch (Exception e) {
                System.err.println("Failed to write rankings XLSX: " + e.getMessage());
            }
        } else {
            System.out.println("No results produced.");
        }
    }

    private static void printComparison(List<StrategyResult> results52, List<StrategyResult> resultsAll) {
        System.out.printf("%n%n%s%n", "═".repeat(120));
        System.out.printf("  COMPARISON: 52 Stocks vs All Stocks (%d strategies)%n", Math.min(results52.size(), resultsAll.size()));
        System.out.printf("%s%n", "═".repeat(120));
        System.out.printf("%-35s │ %7s %8s %8s %7s │ %7s %8s %8s %7s │ %7s%n",
                "Strategy", "Score52", "CAGR52", "MDD52", "WR52",
                "ScoreAl", "CAGRAl", "MDDAl", "WRAl", "Δ Score");
        System.out.printf("%s%n", "─".repeat(120));

        results52.sort((a, b) -> Double.compare(b.getScoreData().getScore(), a.getScoreData().getScore()));

        for (int i = 0; i < results52.size(); i++) {
            var r52 = results52.get(i);
            var rAll = resultsAll.stream()
                    .filter(r -> r.getStrategyName().equals(r52.getStrategyName()))
                    .findFirst().orElse(null);
            if (rAll == null) continue;

            var sd52 = r52.getScoreData();
            var sdAll = rAll.getScoreData();
            double delta = sdAll.getScore() - sd52.getScore();

            System.out.printf("%-35s │ %7.1f %+7.2f%% %7.2f%% %6.1f%% │ %7.1f %+7.2f%% %7.2f%% %6.1f%% │ %+7.1f%n",
                    r52.getStrategyName(),
                    sd52.getScore(), sd52.getAvgCagr(), sd52.getAvgMaxDd(), sd52.getAvgWr(),
                    sdAll.getScore(), sdAll.getAvgCagr(), sdAll.getAvgMaxDd(), sdAll.getAvgWr(),
                    delta);
        }
        System.out.printf("%s%n", "═".repeat(120));
    }
}
