package com.stock.analyzer.backtest.data;

import com.stock.analyzer.backtest.model.Bar;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads OHLCV data from the CSV cache files produced by the Python data fetcher.
 * Cache location: strategies/data/cache/{symbol}_{interval}.csv
 */
public class DataFetcher {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path cacheDir;

    public DataFetcher(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Create a DataFetcher pointing to the default cache directory
     * (strategies/data/cache/ relative to project root).
     */
    public static DataFetcher createDefault() {
        // Try to find the cache dir relative to the working directory
        Path[] candidates = {
                Paths.get("strategies", "data", "cache"),
                Paths.get("..", "strategies", "data", "cache"),
                Paths.get("data", "cache"),
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) return new DataFetcher(p);
        }
        // Fallback: use strategies/data/cache from project root
        return new DataFetcher(Paths.get("strategies", "data", "cache"));
    }

    private String safeFilename(String symbol) {
        return symbol.replace(".", "_").replace("&", "_").replace("%26", "_");
    }

    /**
     * Fetch OHLCV data for a symbol from CSV cache.
     *
     * @param symbol Ticker symbol (e.g. "AAPL", "RELIANCE.NS")
     * @param start  Start date filter (inclusive). Null = no filter.
     * @param interval Bar interval (default "1d")
     * @return List of Bar objects, or empty list if not found.
     */
    public List<Bar> fetchData(String symbol, LocalDate start, String interval) {
        String filename = safeFilename(symbol) + "_" + interval + ".csv";
        Path csvPath = cacheDir.resolve(filename);

        if (!Files.exists(csvPath)) {
            System.err.println("  ⚠ Cache file not found: " + csvPath);
            return new ArrayList<>();
        }

        List<Bar> bars = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String header = reader.readLine(); // skip header
            if (header == null) return bars;

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Bar bar = parseLine(line);
                    if (bar != null) {
                        if (start == null || !bar.getDate().isBefore(start)) {
                            bars.add(bar);
                        }
                    }
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        } catch (IOException e) {
            System.err.println("  ⚠ Error reading " + csvPath + ": " + e.getMessage());
        }

        return bars;
    }

    public List<Bar> fetchData(String symbol, LocalDate start) {
        return fetchData(symbol, start, "1d");
    }

    public List<Bar> fetchData(String symbol, String start) {
        LocalDate startDate = start != null ? LocalDate.parse(start, DATE_FMT) : null;
        return fetchData(symbol, startDate, "1d");
    }

    private Bar parseLine(String line) {
        // CSV format: Date,Open,High,Low,Close,Volume
        String[] parts = line.split(",");
        if (parts.length < 6) return null;

        LocalDate date = parseDate(parts[0].trim());
        if (date == null) return null;

        double open = Double.parseDouble(parts[1].trim());
        double high = Double.parseDouble(parts[2].trim());
        double low = Double.parseDouble(parts[3].trim());
        double close = Double.parseDouble(parts[4].trim());
        double volume = Double.parseDouble(parts[5].trim());

        return new Bar(date, open, high, low, close, volume);
    }

    /**
     * Aggregate daily bars into weekly bars (Monday-Friday → single bar per week).
     * Week boundary = Monday. Each weekly bar uses Monday's date.
     */
    public static List<Bar> resampleToWeekly(List<Bar> dailyBars) {
        if (dailyBars == null || dailyBars.isEmpty()) return new ArrayList<>();

        List<Bar> weekly = new ArrayList<>();
        Bar current = null;

        for (Bar bar : dailyBars) {
            int dow = bar.getDate().getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
            if (current == null || dow == 1) {
                // Start new week
                if (current != null) weekly.add(current);
                current = new Bar(bar.getDate(), bar.getOpen(), bar.getHigh(),
                        bar.getLow(), bar.getClose(), bar.getVolume());
            } else {
                // Extend current week
                current.setHigh(Math.max(current.getHigh(), bar.getHigh()));
                current.setLow(Math.min(current.getLow(), bar.getLow()));
                current.setClose(bar.getClose());
                current.setVolume(current.getVolume() + bar.getVolume());
            }
        }
        if (current != null) weekly.add(current);

        return weekly;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            // Try date-only format first
            return LocalDate.parse(dateStr, DATE_FMT);
        } catch (DateTimeParseException e1) {
            try {
                // Try datetime format
                return LocalDateTime.parse(dateStr, DATETIME_FMT).toLocalDate();
            } catch (DateTimeParseException e2) {
                try {
                    // Try ISO datetime with T
                    return LocalDateTime.parse(dateStr).toLocalDate();
                } catch (DateTimeParseException e3) {
                    return null;
                }
            }
        }
    }
}
