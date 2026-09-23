package com.networth.networthbackend.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.db.Database;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the stock-analyzer report artifacts (my_portfolio.json, all_trades.csv,
 * per_stock_performance.csv) into the net-worth DB, per market. Run locally after pulling the files
 * from the Oracle VM into {@code sector_detail_report/} (India) and {@code sector_detail_report/us/}.
 *
 * <pre>
 *   java -cp target/net-worth-backend-*.jar com.networth.networthbackend.stock.StockDataImporter
 *   java ... StockDataImporter in            # single market
 *   java ... StockDataImporter /abs/report/dir us
 * </pre>
 *
 * Not part of the deployed Lambda path — it's a maintenance/import tool.
 */
@Slf4j
public class StockDataImporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        // args: [baseDir] [market...]  — defaults to importing both markets from ./sector_detail_report
        String base = "sector_detail_report";
        List<String> markets = new ArrayList<>();
        for (String a : args) {
            if (a.equalsIgnoreCase("in") || a.equalsIgnoreCase("us")) markets.add(a.toLowerCase());
            else base = a;
        }
        if (markets.isEmpty()) { markets.add("in"); markets.add("us"); }

        StockPositionRepository posRepo = new StockPositionRepository();
        StockTradeRepository tradeRepo = new StockTradeRepository();
        StockPerformanceRepository perfRepo = new StockPerformanceRepository();

        for (String market : markets) {
            Path dir = market.equals("us") ? Paths.get(base, "us") : Paths.get(base);
            if (!Files.isDirectory(dir)) {
                System.out.printf("[%s] report dir not found: %s — skipping%n", market, dir);
                continue;
            }
            List<StockPosition> positions = readPositions(dir.resolve("my_portfolio.json"), market);
            List<StockTrade> trades = readTrades(dir.resolve("all_trades.csv"), market);
            Map<String, double[]> tradeAgg = aggregateTrades(trades); // key: stock -> [avgPnl, avgMae, avgMfe, avgHoldDays]
            List<StockPerformance> perf = readPerformance(dir.resolve("per_stock_performance.csv"), market, tradeAgg);

            int pw = posRepo.replaceForMarket(market, positions);
            int tw = tradeRepo.replaceForMarket(market, trades);
            int fw = perfRepo.replaceForMarket(market, perf);
            System.out.printf("[%s] imported: %d positions, %d trades, %d performance rows%n", market, pw, tw, fw);
        }
        Database.shutdown();
    }

    // ── my_portfolio.json ──────────────────────────────────────────────
    static List<StockPosition> readPositions(Path file, String market) throws Exception {
        List<StockPosition> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        JsonNode root = MAPPER.readTree(file.toFile());
        JsonNode positions = root.path("positions");
        for (JsonNode p : positions) {
            out.add(new StockPosition(
                    text(p, "id"), market, text(p, "stock"), text(p, "sector"), text(p, "strategy"),
                    date(p, "entry_date"), dbl(p, "entry_price"), dbl(p, "quantity"),
                    text(p, "notes"), textOr(p, "status", "open"), dbl(p, "exit_price"), date(p, "exit_date")));
        }
        return out;
    }

    // ── all_trades.csv ─────────────────────────────────────────────────
    // Header: Sector,Strategy,Stock,TradeNum,EntryDate,ExitDate,EntryPrice,ExitPrice,PnL,PnL%,MAE%,MFE%,BarsHeld,Comment
    static List<StockTrade> readTrades(Path file, String market) throws Exception {
        List<StockTrade> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        try (CSVReader r = new CSVReader(new FileReader(file.toFile()))) {
            String[] row;
            boolean header = true;
            while ((row = r.readNext()) != null) {
                if (header) { header = false; continue; }
                if (row.length < 14) continue;
                out.add(new StockTrade(market, row[0], row[1], row[2], (int) parseD(row[3], 0),
                        parseDate(row[4]), parseDate(row[5]), parseDN(row[6]), parseDN(row[7]),
                        parseDN(row[8]), parseDN(row[9]), parseDN(row[10]), parseDN(row[11]),
                        (Integer) (row[12].isBlank() ? null : (int) parseD(row[12], 0)), row[13]));
            }
        }
        return out;
    }

    /** stock (bare, upper) -> [avgPnlPct, avgMaePct, avgMfePct, avgHoldDays] over closed trades. */
    static Map<String, double[]> aggregateTrades(List<StockTrade> trades) {
        Map<String, double[]> sum = new HashMap<>();   // pnl,mae,mfe,holdDays,count
        for (StockTrade t : trades) {
            String key = t.stock().toUpperCase().replace(".NS", "");
            double[] a = sum.computeIfAbsent(key, k -> new double[5]);
            if (t.pnlPct() != null) a[0] += t.pnlPct();
            if (t.maePct() != null) a[1] += t.maePct();
            if (t.mfePct() != null) a[2] += t.mfePct();
            if (t.entryDate() != null && t.exitDate() != null) a[3] += ChronoUnit.DAYS.between(t.entryDate(), t.exitDate());
            a[4] += 1;
        }
        Map<String, double[]> avg = new HashMap<>();
        for (var e : sum.entrySet()) {
            double[] a = e.getValue();
            double n = a[4] == 0 ? 1 : a[4];
            avg.put(e.getKey(), new double[]{a[0] / n, a[1] / n, a[2] / n, a[3] / n});
        }
        return avg;
    }

    // ── per_stock_performance.csv ──────────────────────────────────────
    // Header: Sector,Strategy,Stock,CAGR,MDD,WR,PF,Trades,Winners,Losers,NetProfit,FinalEquity,Sharpe,AvgBarsHeld
    static List<StockPerformance> readPerformance(Path file, String market, Map<String, double[]> agg) throws Exception {
        List<StockPerformance> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        try (CSVReader r = new CSVReader(new FileReader(file.toFile()))) {
            String[] row;
            boolean header = true;
            while ((row = r.readNext()) != null) {
                if (header) { header = false; continue; }
                if (row.length < 14) continue;
                String bare = row[2].toUpperCase().replace(".NS", "");
                double[] a = agg.getOrDefault(bare, null);
                out.add(new StockPerformance(market, row[0], row[1], row[2],
                        parseDN(row[3]), parseDN(row[4]), parseDN(row[5]), parseDN(row[6]),
                        (int) parseD(row[7], 0), (int) parseD(row[8], 0), (int) parseD(row[9], 0),
                        parseDN(row[10]), parseDN(row[11]), parseDN(row[12]), parseDN(row[13]),
                        a == null ? null : round(a[0]), a == null ? null : round(a[1]),
                        a == null ? null : round(a[2]), a == null ? null : round(a[3])));
            }
        }
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────
    private static Double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
    private static String text(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asText() : null; }
    private static String textOr(JsonNode n, String f, String d) { return n.hasNonNull(f) ? n.get(f).asText() : d; }
    private static Double dbl(JsonNode n, String f) { return n.hasNonNull(f) ? n.get(f).asDouble() : null; }
    private static LocalDate date(JsonNode n, String f) {
        String s = text(n, f);
        return s == null || s.isBlank() ? null : parseDate(s);
    }
    private static LocalDate parseDate(String s) {
        try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s.trim()); }
        catch (Exception e) { return null; }
    }
    private static double parseD(String s, double d) {
        try { return (s == null || s.isBlank()) ? d : Double.parseDouble(s.trim()); }
        catch (Exception e) { return d; }
    }
    private static Double parseDN(String s) {
        try { return (s == null || s.isBlank()) ? null : Double.parseDouble(s.trim()); }
        catch (Exception e) { return null; }
    }
}
