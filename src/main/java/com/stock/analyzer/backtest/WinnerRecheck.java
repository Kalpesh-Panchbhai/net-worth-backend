package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.strategy.Strategy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Re-evaluates each sector's CURRENT winner plus the top-10 alternates from the
 * last full optimization run against today's OHLC data. Writes a comparison CSV
 * showing whether the current winner is still the best contender.
 *
 *   Sector | CurrentWinner | CurComposite | BestNow | BestComposite | Changed?
 *
 * Run:
 *   mvn -q exec:java -Dexec.mainClass=com.stock.analyzer.backtest.WinnerRecheck
 */
public class WinnerRecheck {

    private static final Path WINNERS_CSV = Paths.get("sector_all_phases", "new_winners.csv");
    private static final Path TOP10_CSV   = Paths.get("sector_all_phases", "top10_per_sector.csv");
    private static final Path OUT_CSV     = Paths.get("sector_all_phases", "winner_recheck.csv");

    public static void main(String[] args) throws Exception {
        Map<String, String> winners = loadWinners();
        Map<String, List<String>> top10 = loadTop10();

        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();

        System.out.println("=== Winner Recheck (post 2026-05-29 data refresh) ===");

        try (BufferedWriter out = new BufferedWriter(new FileWriter(OUT_CSV.toFile()))) {
            out.write("Sector,CurrentWinner,CurComposite,BestNow,BestComposite,Improvement,Changed,Tested");
            out.newLine();

            int totalChanged = 0;

            for (Map.Entry<String, String> e : winners.entrySet()) {
                String sector = e.getKey();
                String current = e.getValue();

                // Candidate set: current winner + all top-10 alternates (deduped)
                LinkedHashSet<String> candidates = new LinkedHashSet<>();
                candidates.add(current);
                List<String> top = top10.getOrDefault(sector, List.of());
                candidates.addAll(top);

                // Load sector bars once
                Path sectorFile = Paths.get("src/main/resources/sectors/" + sector + ".txt");
                List<String> stocks = Files.readAllLines(sectorFile);
                stocks.removeIf(s -> s == null || s.trim().isEmpty());

                LinkedHashMap<String, List<Bar>> barsByStock = new LinkedHashMap<>();
                for (String stock : stocks) {
                    List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
                    if (bars != null && bars.size() >= 50) barsByStock.put(stock, bars);
                }

                String bestName = current;
                double bestComp = Double.NEGATIVE_INFINITY;
                double curComp = Double.NaN;

                long t0 = System.currentTimeMillis();
                for (String varName : candidates) {
                    Supplier<Strategy> f = registry.get(varName);
                    if (f == null) {
                        System.err.printf("[%s] WARN: %s not in registry — skipping%n", sector, varName);
                        continue;
                    }
                    SectorAllPhasesOptimizer.VariantResult vr;
                    try {
                        vr = SectorAllPhasesOptimizer.evaluate(varName, f, barsByStock);
                    } catch (Throwable t) {
                        System.err.printf("[%s] %s eval error: %s%n", sector, varName, t);
                        continue;
                    }
                    if (vr == null) continue;
                    double c = SectorAllPhasesOptimizer.composite(vr);
                    if (varName.equals(current)) curComp = c;
                    if (c > bestComp) {
                        bestComp = c;
                        bestName = varName;
                    }
                }

                boolean changed = !bestName.equals(current);
                if (changed) totalChanged++;

                long ms = System.currentTimeMillis() - t0;
                double improvement = bestComp - curComp;
                System.out.printf("[%s] cur=%-40s comp=%.4f  best=%-40s comp=%.4f  %s%s  (%.1fs)%n",
                    sector, current, curComp, bestName, bestComp,
                    changed ? "CHANGED" : "stable",
                    changed ? String.format(" Δ=+%.4f", improvement) : "",
                    ms / 1000.0);

                out.write(String.format(Locale.US,
                    "%s,%s,%.4f,%s,%.4f,%+.4f,%s,%d",
                    sector, current, curComp, bestName, bestComp,
                    improvement, changed ? "YES" : "no", candidates.size()));
                out.newLine();
                out.flush();
            }

            System.out.printf("%n=== Summary ===%n");
            System.out.printf("Sectors with new winner: %d / %d%n", totalChanged, winners.size());
            System.out.printf("Output: %s%n", OUT_CSV.toAbsolutePath());
        }
    }

    private static Map<String, String> loadWinners() throws Exception {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(WINNERS_CSV)) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 2) out.put(p[0].trim(), p[1].trim());
            }
        }
        return out;
    }

    private static Map<String, List<String>> loadTop10() throws Exception {
        LinkedHashMap<String, List<String>> out = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(TOP10_CSV)) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 3) continue;
                out.computeIfAbsent(p[0].trim(), k -> new ArrayList<>()).add(p[2].trim());
            }
        }
        return out;
    }
}
