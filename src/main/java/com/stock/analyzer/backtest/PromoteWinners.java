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
 * Promotes new winners from {@code full_recheck_compare.csv} into the production
 * {@code new_winners.csv}.
 *
 * For each sector where {@code Improvement > 0} and {@code Changed=YES}:
 *   1. Run the full {@link SectorAllPhasesOptimizer#evaluate} (includes DSR /
 *      portfolio Sharpe) on the new winner so we get every column that
 *      {@code new_winners.csv} expects.
 *   2. Replace that sector's row in {@code new_winners.csv} with the new data.
 *
 * Sectors with {@code Improvement <= 0} keep their old row untouched.
 *
 * Run:
 *   mvn -q exec:java -Dexec.mainClass=com.stock.analyzer.backtest.PromoteWinners
 */
public class PromoteWinners {

    private static final Path WINNERS_CSV = Paths.get("sector_all_phases", "new_winners.csv");
    private static final Path COMPARE_CSV = Paths.get("sector_all_phases", "full_recheck_compare.csv");
    private static final Path BACKUP_CSV  = Paths.get("sector_all_phases", "new_winners.csv.bak");

    public static void main(String[] args) throws Exception {
        // 1. Read existing winners (preserve order + full rows).
        LinkedHashMap<String, String> oldRows = readWinners();
        if (oldRows.isEmpty()) throw new IllegalStateException("new_winners.csv empty");

        // 2. Read compare.csv to find sectors to promote.
        Map<String, String> promotions = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(COMPARE_CSV)) {
            r.readLine();   // header
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length < 7) continue;
                String sector = p[0].trim();
                String newWin = p[3].trim();
                double improvement = Double.parseDouble(p[5].trim());
                String changed = p[6].trim();
                if ("YES".equalsIgnoreCase(changed) && improvement > 0.0) {
                    promotions.put(sector, newWin);
                }
            }
        }
        System.out.printf("=== Promote Winners ===%nFound %d sectors to promote.%n", promotions.size());

        if (promotions.isEmpty()) {
            System.out.println("No changes — exiting.");
            return;
        }

        // 3. Backup current winners file.
        Files.copy(WINNERS_CSV, BACKUP_CSV, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Backup → " + BACKUP_CSV);

        // 4. Run full evaluate() on each new winner.
        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();
        Map<String, String> newRows = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : promotions.entrySet()) {
            String sector = e.getKey();
            String varName = e.getValue();
            Supplier<Strategy> f = registry.get(varName);
            if (f == null) {
                System.err.printf("[%s] %s missing from registry — skipping promotion%n", sector, varName);
                continue;
            }

            List<String> stocks = Files.readAllLines(
                Paths.get("src/main/resources/sectors/" + sector + ".txt"));
            stocks.removeIf(s -> s == null || s.trim().isEmpty());

            LinkedHashMap<String, List<Bar>> barsByStock = new LinkedHashMap<>();
            for (String stock : stocks) {
                List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
                if (bars != null && bars.size() >= 50) barsByStock.put(stock, bars);
            }

            long t0 = System.currentTimeMillis();
            SectorAllPhasesOptimizer.VariantResult vr =
                SectorAllPhasesOptimizer.evaluate(varName, f, barsByStock);
            long ms = System.currentTimeMillis() - t0;
            if (vr == null) {
                System.err.printf("[%s] evaluate returned null — skipping%n", sector);
                continue;
            }
            double comp = SectorAllPhasesOptimizer.composite(vr);

            // Format row matching new_winners.csv schema:
            // Sector,Strategy,Composite,IS_Full_CAGR,IS_Early_CAGR,OOS_Hist_CAGR,OOS_Recent_CAGR,
            // MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest,StocksUsed
            String row = String.format(Locale.US,
                "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%d",
                sector, varName, comp,
                vr.isFull.getAvgCagr(),
                vr.isEarly.getAvgCagr(),
                vr.oosHist.getAvgCagr(),
                vr.oosRecent.getAvgCagr(),
                vr.minCagr,
                vr.maxMdd,
                vr.minPf,
                vr.minWr,
                vr.portSharpe,
                vr.dsrVest,
                vr.stocksUsed);
            newRows.put(sector, row);
            System.out.printf("[%s] %s comp=%.4f  Sharpe=%.4f  DSR=%.4f  (%.1fs)%n",
                sector, varName, comp, vr.portSharpe, vr.dsrVest, ms / 1000.0);
        }

        // 5. Merge: oldRows for stable sectors, newRows for promoted.
        try (BufferedWriter w = new BufferedWriter(new FileWriter(WINNERS_CSV.toFile()))) {
            w.write("Sector,Strategy,Composite,IS_Full_CAGR,IS_Early_CAGR,OOS_Hist_CAGR,OOS_Recent_CAGR,"
                + "MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest,StocksUsed");
            w.newLine();
            for (Map.Entry<String, String> e : oldRows.entrySet()) {
                String sector = e.getKey();
                String row = newRows.containsKey(sector) ? newRows.get(sector) : e.getValue();
                w.write(row);
                w.newLine();
            }
        }
        System.out.printf("%nWrote %s with %d promotions (rest preserved)%n",
            WINNERS_CSV, newRows.size());
    }

    private static LinkedHashMap<String, String> readWinners() throws Exception {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(WINNERS_CSV)) {
            r.readLine();   // header
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 2) continue;
                out.put(p[0].trim(), line);
            }
        }
        return out;
    }
}
