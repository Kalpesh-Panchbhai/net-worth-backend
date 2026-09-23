package com.stock.analyzer.backtest.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates everything that differs between the markets the analyzer
 * supports. Selected via the {@code -Dmarket=<code>} JVM system property
 * (default {@code "in"}), so a single JAR drives both pipelines.
 *
 * <ul>
 *   <li><b>in</b> — India / NSE (Nifty 500, 18 NSE sectors). Uses the original
 *       root-level paths so the existing pipeline is byte-for-byte unchanged.</li>
 *   <li><b>us</b> — United States (S&amp;P 500, 11 GICS sectors). Reads the
 *       {@code sectors_us} universe and writes into per-market "us" sub-dirs so
 *       it never collides with India data.</li>
 * </ul>
 *
 * The per-sector winning strategy is read from {@code new_winners.csv} at
 * runtime ({@link #loadWinners()}) — the single source of truth produced by
 * {@code SectorAllPhasesOptimizer} — replacing the old hand-copied arrays.
 */
public final class Market {

    private final String code;

    private Market(String code) {
        this.code = code;
    }

    /** Resolve the active market from the {@code -Dmarket} system property. */
    public static Market current() {
        return of(System.getProperty("market", "in"));
    }

    public static Market of(String code) {
        String c = code == null ? "in" : code.trim().toLowerCase();
        if (c.equals("us") || c.equals("usa")) return new Market("us");
        return new Market("in");
    }

    public boolean isUs() { return code.equals("us"); }

    public String code() { return code; }

    /** IANA timezone the exchange trades in (used for Yahoo bar-date mapping). */
    public String timezone() {
        return isUs() ? "America/New_York" : "Asia/Kolkata";
    }

    /** Default full-universe file on the classpath (used by BacktestMain). */
    public String universeFile() {
        return isUs() ? "us_sp500.txt" : "nifty500.txt";
    }

    /** Classpath directory holding the per-sector ticker lists. */
    public String sectorsResourceDir() {
        return isUs() ? "sectors_us" : "sectors";
    }

    /** Filesystem directory holding the per-sector ticker lists. */
    public Path sectorsFsDir() {
        return isUs() ? Paths.get("src", "main", "resources", "sectors_us")
                      : Paths.get("src", "main", "resources", "sectors");
    }

    /** Optimizer output dir (new_winners.csv, top10_per_sector.csv, ...). */
    public Path phasesDir() {
        return isUs() ? Paths.get("sector_all_phases", "us")
                      : Paths.get("sector_all_phases");
    }

    /** Detail-report output dir (per_stock_performance.csv, signals.csv, ...). */
    public Path reportDir() {
        return isUs() ? Paths.get("sector_detail_report", "us")
                      : Paths.get("sector_detail_report");
    }

    /** Canonical source of truth for the per-sector winning strategy. */
    public Path winnersCsv() {
        return phasesDir().resolve("new_winners.csv");
    }

    /** Discover sector names from the ticker-list files (sorted, stable). */
    public List<String> sectors() {
        List<String> out = new ArrayList<>();
        Path dir = sectorsFsDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.txt")) {
                for (Path p : ds) {
                    String n = p.getFileName().toString();
                    out.add(n.substring(0, n.length() - ".txt".length()));
                }
            } catch (IOException e) {
                System.err.println("  ⚠ Market.sectors(): " + e.getMessage());
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Read the per-sector winners from {@code new_winners.csv} (ordered).
     * Each element is {@code [sector, strategyName]}. Empty if the file is
     * missing (callers may fall back to a hardcoded default).
     */
    public List<String[]> loadWinners() {
        List<String[]> out = new ArrayList<>();
        Path csv = winnersCsv();
        if (!Files.exists(csv)) return out;
        try (BufferedReader br = Files.newBufferedReader(csv)) {
            br.readLine(); // header: Sector,Strategy,...
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    out.add(new String[]{parts[0].trim(), parts[1].trim()});
                }
            }
        } catch (IOException e) {
            System.err.println("  ⚠ Market.loadWinners(): " + e.getMessage());
        }
        return out;
    }

    /**
     * Load the ticker list for one sector — classpath first (works from the
     * shaded JAR), filesystem fallback (works from the repo root).
     */
    public List<String> loadSectorStocks(String sector) {
        List<String> stocks = new ArrayList<>();
        String resource = sectorsResourceDir() + "/" + sector + ".txt";
        try (InputStream is = Market.class.getClassLoader().getResourceAsStream(resource)) {
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) stocks.add(line);
                    }
                }
                return stocks;
            }
        } catch (IOException e) {
            // fall through to filesystem
        }
        Path f = sectorsFsDir().resolve(sector + ".txt");
        if (Files.exists(f)) {
            try {
                for (String l : Files.readAllLines(f)) {
                    l = l.trim();
                    if (!l.isEmpty()) stocks.add(l);
                }
            } catch (IOException e) {
                System.err.println("  ⚠ Market.loadSectorStocks(" + sector + "): " + e.getMessage());
            }
        }
        return stocks;
    }
}
