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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Full re-evaluation: every strategy in VariantRegistry × every sector.
 *
 * Writes to:
 *   sector_all_phases/full_recheck_winners.csv     — best per sector
 *   sector_all_phases/full_recheck_top10.csv       — top 10 per sector
 *   sector_all_phases/full_recheck_compare.csv     — vs current new_winners.csv
 *
 * Does NOT overwrite the production new_winners.csv. Approve manually first.
 *
 * Run:
 *   mvn -q exec:java -Dexec.mainClass=com.stock.analyzer.backtest.FullRecheck
 */
public class FullRecheck {

    private static final String[] SECTORS = {
        "automobile_and_auto_components", "capital_goods", "chemicals", "construction",
        "construction_materials", "consumer_durables", "consumer_services",
        "fast_moving_consumer_goods", "financial_services", "healthcare",
        "information_technology", "metals_and_mining", "oil_gas_and_consumable_fuels",
        "power", "realty", "services", "telecommunication", "textiles"
    };

    private static final Path OUT_DIR     = Paths.get("sector_all_phases");
    private static final Path WINNERS_OUT = OUT_DIR.resolve("full_recheck_winners.csv");
    private static final Path TOP10_OUT   = OUT_DIR.resolve("full_recheck_top10.csv");
    private static final Path COMPARE_OUT = OUT_DIR.resolve("full_recheck_compare.csv");
    private static final Path CUR_WINNERS = OUT_DIR.resolve("new_winners.csv");

    private static final int PROGRESS_EVERY = 200;

    public static void main(String[] args) throws Exception {
        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();
        Map<String, String> currentWinners = loadCurrentWinners();

        System.out.printf("=== FullRecheck — %d variants × %d sectors ===%n",
            registry.size(), SECTORS.length);
        System.out.printf("Threads: %d%n%n", Runtime.getRuntime().availableProcessors());

        try (BufferedWriter winners = new BufferedWriter(new FileWriter(WINNERS_OUT.toFile()));
             BufferedWriter top10   = new BufferedWriter(new FileWriter(TOP10_OUT.toFile()));
             BufferedWriter compare = new BufferedWriter(new FileWriter(COMPARE_OUT.toFile()))) {

            winners.write("Sector,Strategy,Composite,MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest");
            winners.newLine();
            top10.write("Sector,Rank,Strategy,Composite,MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest");
            top10.newLine();
            compare.write("Sector,CurrentWinner,CurComposite,NewWinner,NewComposite,Improvement,Changed");
            compare.newLine();

            long globalT0 = System.currentTimeMillis();

            for (String sector : SECTORS) {
                long secT0 = System.currentTimeMillis();
                System.out.printf("[%s] starting…%n", sector);

                List<String> stocks = Files.readAllLines(
                    Paths.get("src/main/resources/sectors/" + sector + ".txt"));
                stocks.removeIf(s -> s == null || s.trim().isEmpty());

                LinkedHashMap<String, List<Bar>> barsByStock = new LinkedHashMap<>();
                for (String stock : stocks) {
                    List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
                    if (bars != null && bars.size() >= 50) barsByStock.put(stock, bars);
                }

                final List<String> variantNames = new ArrayList<>(registry.keySet());
                final int total = variantNames.size();
                final AtomicInteger progress = new AtomicInteger();
                final AtomicInteger errors = new AtomicInteger();
                final long sectorStart = secT0;

                List<SectorAllPhasesOptimizer.VariantResult> results = variantNames.parallelStream()
                    .map(name -> {
                        Supplier<Strategy> f = registry.get(name);
                        if (f == null) return null;
                        SectorAllPhasesOptimizer.VariantResult vr = null;
                        try {
                            vr = SectorAllPhasesOptimizer.evaluateFast(name, f, barsByStock);
                        } catch (Throwable t) {
                            if (errors.incrementAndGet() <= 3) {
                                System.err.println("  ✗ " + name + ": " + t);
                            }
                        }
                        int done = progress.incrementAndGet();
                        if (done % PROGRESS_EVERY == 0 || done == total) {
                            long el = System.currentTimeMillis() - sectorStart;
                            double eta = total > done ? el * (total - done) / (double) done / 1000.0 : 0;
                            System.out.printf("    [%s] %d/%d  err=%d  elapsed=%.0fs  eta=%.0fs%n",
                                sector, done, total, errors.get(), el / 1000.0, eta);
                        }
                        return vr;
                    })
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

                if (results.isEmpty()) {
                    System.err.printf("  [%s] no results — skipping%n", sector);
                    continue;
                }

                // Composite + name
                List<Scored> scored = new ArrayList<>(results.size());
                for (SectorAllPhasesOptimizer.VariantResult vr : results) {
                    double c = SectorAllPhasesOptimizer.composite(vr);
                    scored.add(new Scored(vr.strategyName, c, vr));
                }
                scored.sort((a, b) -> Double.compare(b.comp, a.comp));

                Scored best = scored.get(0);
                long ms = System.currentTimeMillis() - secT0;
                System.out.printf("  [%s] winner=%s  comp=%.4f  [%.1fs]%n",
                    sector, best.name, best.comp, ms / 1000.0);

                // winners CSV
                winners.write(String.format(Locale.US,
                    "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f",
                    sector, best.name, best.comp,
                    best.vr.minCagr, best.vr.maxMdd, best.vr.minPf, best.vr.minWr,
                    best.vr.portSharpe, best.vr.dsrVest));
                winners.newLine();
                winners.flush();

                // top 10
                int top = Math.min(10, scored.size());
                for (int i = 0; i < top; i++) {
                    Scored s = scored.get(i);
                    top10.write(String.format(Locale.US,
                        "%s,%d,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f",
                        sector, i + 1, s.name, s.comp,
                        s.vr.minCagr, s.vr.maxMdd, s.vr.minPf, s.vr.minWr,
                        s.vr.portSharpe, s.vr.dsrVest));
                    top10.newLine();
                }
                top10.flush();

                // compare vs current
                String curName = currentWinners.getOrDefault(sector, "?");
                Scored curScored = scored.stream()
                    .filter(s -> s.name.equals(curName))
                    .findFirst().orElse(null);
                double curComp = curScored != null ? curScored.comp : Double.NaN;
                boolean changed = !best.name.equals(curName);
                compare.write(String.format(Locale.US,
                    "%s,%s,%.4f,%s,%.4f,%+.4f,%s",
                    sector, curName, curComp, best.name, best.comp,
                    best.comp - curComp, changed ? "YES" : "no"));
                compare.newLine();
                compare.flush();
            }

            long totalMs = System.currentTimeMillis() - globalT0;
            System.out.printf("%nDone in %.1f min%n", totalMs / 60000.0);
            System.out.printf("  %s%n  %s%n  %s%n", WINNERS_OUT, TOP10_OUT, COMPARE_OUT);
        }
    }

    private static Map<String, String> loadCurrentWinners() throws Exception {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(CUR_WINNERS)) {
            r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 2) out.put(p[0].trim(), p[1].trim());
            }
        }
        return out;
    }

    private static final class Scored {
        final String name;
        final double comp;
        final SectorAllPhasesOptimizer.VariantResult vr;
        Scored(String name, double comp, SectorAllPhasesOptimizer.VariantResult vr) {
            this.name = name; this.comp = comp; this.vr = vr;
        }
    }
}
