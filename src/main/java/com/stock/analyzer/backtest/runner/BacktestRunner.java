package com.stock.analyzer.backtest.runner;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.ranking.Scorer;
import com.stock.analyzer.backtest.strategy.Strategy;
import com.stock.analyzer.backtest.strategy.StrategyData;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Multi-threaded backtest runner.
 * Runs each strategy on all assets in its own thread via ExecutorService.
 */
public class BacktestRunner {

    private final LinkedHashMap<String, Supplier<Strategy>> strategies;
    private final List<Asset> assets;
    private final double years;
    private final DataFetcher dataFetcher;
    private final Map<String, List<Bar>> preloadedData = new ConcurrentHashMap<>();
    private String rankingsFile = BacktestConfig.RANKINGS_FILE;

    public BacktestRunner() {
        this(null, null);
    }

    public BacktestRunner(String filter) {
        this(filter, null);
    }

    public BacktestRunner(String filter, List<Asset> customAssets) {
        LinkedHashMap<String, Supplier<Strategy>> all = VariantRegistry.buildAll();
        if (filter != null && !filter.isEmpty()) {
            LinkedHashMap<String, Supplier<Strategy>> filtered = new LinkedHashMap<>();
            String prefix = filter;
            for (var entry : all.entrySet()) {
                if (entry.getKey().equals(prefix) || entry.getKey().startsWith(prefix + ":")) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            if (filtered.isEmpty()) {
                // Try exact match (case-sensitive)
                if (all.containsKey(filter)) {
                    filtered.put(filter, all.get(filter));
                } else {
                    System.err.println("No strategies matching '" + filter + "'. Available prefixes: SMC_R1, V2_R1, V3_R1, etc.");
                }
            }
            this.strategies = filtered;
        } else {
            this.strategies = all;
        }
        this.assets = customAssets != null ? customAssets : BacktestConfig.ASSETS;
        this.dataFetcher = DataFetcher.createDefault();

        LocalDate tradeFrom = LocalDate.parse(BacktestConfig.TRADE_FROM);
        LocalDate now = LocalDate.now();
        this.years = ChronoUnit.DAYS.between(tradeFrom, now) / 365.25;

        // Preload all CSV data once — shared across all threads (read-only)
        preloadData();
    }

    public void setRankingsFile(String path) {
        this.rankingsFile = path;
    }

    private void preloadData() {
        System.out.println("  Preloading data for " + assets.size() + " assets...");
        long t0 = System.currentTimeMillis();
        for (Asset asset : assets) {
            List<Bar> bars = dataFetcher.fetchData(asset.getSymbol(), BacktestConfig.WARMUP_START);
            if (bars != null && bars.size() >= 50) {
                preloadedData.put(asset.getSymbol(), bars);
            }
        }
        long dt = System.currentTimeMillis() - t0;
        System.out.printf("  Loaded %d/%d assets in %d ms%n%n", preloadedData.size(), assets.size(), dt);
    }

    public List<StrategyResult> run() {
        int nStrategies = strategies.size();
        int nAssets = assets.size();

        System.out.printf("%n%s%n", "═".repeat(70));
        System.out.printf("  Running %d strategies × %d assets  |  %d threads (1 per variant)%n", nStrategies, nAssets, nStrategies);
        System.out.printf("%s%n%n", "═".repeat(70));

        int nThreads = Math.min(nStrategies, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<Future<StrategyResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        for (var entry : strategies.entrySet()) {
            String stratKey = entry.getKey();
            Supplier<Strategy> factory = entry.getValue();

            futures.add(executor.submit(() -> {
                StrategyResult result = runOneStrategy(factory, stratKey);
                int done = completed.incrementAndGet();
                if (result != null) {
                    ScoreData sd = result.getScoreData();
                    System.out.printf("  [%3d/%d] %-35s  Score: %5.1f  |  Calmar: %5.3f  |  %%Prof: %.1f%%  |  Cons: %.3f  |  CAGR: %+6.2f%%  |  MDD: %6.2f%%%n",
                            done, nStrategies, result.getStrategyName(),
                            sd.getScore(), sd.getAvgCalmar(), sd.getPctProfitable()*100, sd.getConsistency(),
                            sd.getAvgCagr(), sd.getAvgMaxDd());
                } else {
                    System.out.printf("  [%3d/%d] %-35s  ERROR%n", done, nStrategies, stratKey);
                }
                return result;
            }));
        }

        List<StrategyResult> results = new ArrayList<>();
        for (Future<StrategyResult> f : futures) {
            try {
                StrategyResult r = f.get();
                if (r != null) results.add(r);
            } catch (Exception e) {
                System.err.println("  Strategy failed: " + e.getMessage());
            }
        }

        executor.shutdown();

        // Cross-strategy normalization (industry-standard min-max across all strategies)
        if (results.size() > 1) {
            List<ScoreData> allScores = new ArrayList<>();
            for (StrategyResult r : results) allScores.add(r.getScoreData());
            Scorer.normalizeScores(allScores);
        }

        return results;
    }

    private StrategyResult runOneStrategy(Supplier<Strategy> factory, String stratKey) {
        try {
            Strategy strategy = factory.get();
            Map<String, Metrics> perAssetMetrics = new LinkedHashMap<>();

            for (Asset asset : assets) {
                Metrics m = runOnAsset(strategy, asset.getSymbol());
                if (m != null) perAssetMetrics.put(asset.getSymbol(), m);
                strategy.reset();
            }

            ScoreData scoreData = Scorer.computeScore(perAssetMetrics, years);

            return new StrategyResult(strategy.getName(), strategy.getTimeframe(), scoreData);
        } catch (Exception e) {
            System.err.println("  " + stratKey + " failed: " + e.getMessage());
            return null;
        }
    }

    private Metrics runOnAsset(Strategy strategy, String symbol) {
        try {
            List<Bar> bars = preloadedData.get(symbol);
            if (bars == null || bars.size() < 50) {
                return null;
            }

            // Resample to weekly if strategy uses "1W" timeframe
            if ("1W".equals(strategy.getTimeframe())) {
                bars = com.stock.analyzer.backtest.data.DataFetcher.resampleToWeekly(bars);
                if (bars.size() < 20) return null;
            }

            StrategyData data = strategy.computeIndicators(bars);

            LocalDate tradeFromDt = LocalDate.parse(BacktestConfig.TRADE_FROM);

            TradingViewEngine engine = new TradingViewEngine(
                    BacktestConfig.INITIAL_CAPITAL,
                    BacktestConfig.COMMISSION_PCT,
                    BacktestConfig.SLIPPAGE,
                    "percent_of_equity",
                    100.0,
                    0
            );

            int n = bars.size();
            for (int i = 0; i < n; i++) {
                Bar bar = bars.get(i);
                engine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
                boolean tradeActive = !bar.getDate().isBefore(tradeFromDt);
                strategy.onBar(engine, data, i, tradeActive);
            }

            Bar lastBar = bars.get(n - 1);
            engine.finalize(lastBar.getDate(), lastBar.getClose());
            return engine.getMetrics();
        } catch (Exception e) {
            return null;
        }
    }

    public void printRankings(List<StrategyResult> results) {
        results.sort((a, b) -> Double.compare(b.getScoreData().getScore(), a.getScoreData().getScore()));

        System.out.printf("%n%n%s%n", "═".repeat(110));
        System.out.printf("  FINAL RANKINGS  (%d strategies)  |  Score = 90%% Calmar + 10%% Robustness%n", results.size());
        System.out.printf("%s%n", "═".repeat(110));
        System.out.printf("%-6s %-35s %-5s %7s %8s %8s %8s %8s %7s %7s%n",
                "Rank", "Strategy", "TF", "Score", "Calmar", "Robust", "CAGR%", "MaxDD%", "PF", "WR%");
        System.out.printf("%s%n", "─".repeat(110));

        for (int rank = 0; rank < results.size(); rank++) {
            StrategyResult r = results.get(rank);
            ScoreData sd = r.getScoreData();
            System.out.printf("%-6d %-35s %-5s %7.1f %8.1f %8.1f %+7.2f%% %7.2f%% %7.2f %6.1f%%%n",
                    rank + 1, r.getStrategyName(), r.getTimeframe(),
                    sd.getScore(), sd.getCalmarScore(), sd.getRobustnessScore(),
                    sd.getAvgCagr(), sd.getAvgMaxDd(), sd.getAvgPf(), sd.getAvgWr());
        }
        System.out.printf("%s%n", "═".repeat(110));
    }

    public void writeRankings(List<StrategyResult> results) throws IOException {
        Scorer.writeRankings(results, rankingsFile);
        System.out.printf("  Rankings saved to: %s%n", rankingsFile);
    }
}
