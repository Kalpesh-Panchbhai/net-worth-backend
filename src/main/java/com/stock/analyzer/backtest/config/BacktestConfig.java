package com.stock.analyzer.backtest.config;

import com.stock.analyzer.backtest.model.Asset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Central configuration for the backtesting framework.
 * Mirrors strategies/config.py exactly.
 */
public final class BacktestConfig {

    private BacktestConfig() {}

    // ── Assets to test every strategy on ──────────────────────────
    public static final List<Asset> ASSETS = Arrays.asList(
        // IT / Software
        new Asset("TCS.NS",        "TCS",            "NSE",    "INR", "IT"),
        new Asset("INFY.NS",       "Infosys",        "NSE",    "INR", "IT"),
        new Asset("HCLTECH.NS",    "HCL Tech",       "NSE",    "INR", "IT"),
        new Asset("WIPRO.NS",      "Wipro",          "NSE",    "INR", "IT"),
        new Asset("TECHM.NS",      "Tech Mahindra",  "NSE",    "INR", "IT"),
        // Banking
        new Asset("HDFCBANK.NS",   "HDFC Bank",      "NSE",    "INR", "Banking"),
        new Asset("ICICIBANK.NS",  "ICICI Bank",     "NSE",    "INR", "Banking"),
        new Asset("SBIN.NS",       "SBI",            "NSE",    "INR", "Banking"),
        new Asset("KOTAKBANK.NS",  "Kotak Bank",     "NSE",    "INR", "Banking"),
        new Asset("AXISBANK.NS",   "Axis Bank",      "NSE",    "INR", "Banking"),
        // Pharma
        new Asset("SUNPHARMA.NS",  "Sun Pharma",     "NSE",    "INR", "Pharma"),
        new Asset("DRREDDY.NS",    "Dr Reddy",       "NSE",    "INR", "Pharma"),
        new Asset("CIPLA.NS",      "Cipla",          "NSE",    "INR", "Pharma"),
        new Asset("DIVISLAB.NS",   "Divis Lab",      "NSE",    "INR", "Pharma"),
        new Asset("LUPIN.NS",      "Lupin",          "NSE",    "INR", "Pharma"),
        // Automobile
        new Asset("MARUTI.NS",     "Maruti",         "NSE",    "INR", "Auto"),
        new Asset("M&M.NS",        "M&M",            "NSE",    "INR", "Auto"),
        new Asset("BAJAJ-AUTO.NS", "Bajaj Auto",     "NSE",    "INR", "Auto"),
        new Asset("EICHERMOT.NS",  "Eicher Motors",  "NSE",    "INR", "Auto"),
        new Asset("HEROMOTOCO.NS", "Hero Moto",      "NSE",    "INR", "Auto"),
        // FMCG
        new Asset("HINDUNILVR.NS", "HUL",            "NSE",    "INR", "FMCG"),
        new Asset("ITC.NS",        "ITC",            "NSE",    "INR", "FMCG"),
        new Asset("BRITANNIA.NS",  "Britannia",      "NSE",    "INR", "FMCG"),
        new Asset("NESTLEIND.NS",  "Nestle India",   "NSE",    "INR", "FMCG"),
        new Asset("DABUR.NS",      "Dabur",          "NSE",    "INR", "FMCG"),
        // Energy / Oil & Gas
        new Asset("RELIANCE.NS",   "Reliance",       "NSE",    "INR", "Energy"),
        new Asset("ONGC.NS",       "ONGC",           "NSE",    "INR", "Energy"),
        new Asset("BPCL.NS",       "BPCL",           "NSE",    "INR", "Energy"),
        new Asset("IOC.NS",        "IOC",            "NSE",    "INR", "Energy"),
        new Asset("GAIL.NS",       "GAIL",           "NSE",    "INR", "Energy"),
        // Metals & Mining
        new Asset("TATASTEEL.NS",  "Tata Steel",     "NSE",    "INR", "Metals"),
        new Asset("JSWSTEEL.NS",   "JSW Steel",      "NSE",    "INR", "Metals"),
        new Asset("HINDALCO.NS",   "Hindalco",       "NSE",    "INR", "Metals"),
        new Asset("VEDL.NS",       "Vedanta",        "NSE",    "INR", "Metals"),
        new Asset("COALINDIA.NS",  "Coal India",     "NSE",    "INR", "Metals"),
        // Finance (NBFC/Insurance)
        new Asset("BAJFINANCE.NS", "Bajaj Finance",  "NSE",    "INR", "Finance"),
        new Asset("BAJAJFINSV.NS", "Bajaj Finserv",  "NSE",    "INR", "Finance"),
        new Asset("CHOLAFIN.NS",   "Chola Finance",  "NSE",    "INR", "Finance"),
        new Asset("SHRIRAMFIN.NS", "Shriram Fin",    "NSE",    "INR", "Finance"),
        new Asset("MUTHOOTFIN.NS", "Muthoot Fin",    "NSE",    "INR", "Finance"),
        // Capital Goods / Defence
        new Asset("LT.NS",         "L&T",            "NSE",    "INR", "Capital Goods"),
        new Asset("SIEMENS.NS",    "Siemens",        "NSE",    "INR", "Capital Goods"),
        new Asset("ABB.NS",        "ABB India",      "NSE",    "INR", "Capital Goods"),
        new Asset("HAL.NS",        "HAL",            "NSE",    "INR", "Capital Goods"),
        new Asset("BEL.NS",        "BEL",            "NSE",    "INR", "Capital Goods"),
        // Real Estate
        new Asset("DLF.NS",        "DLF",            "NSE",    "INR", "Real Estate"),
        new Asset("GODREJPROP.NS", "Godrej Prop",    "NSE",    "INR", "Real Estate"),
        new Asset("OBEROIRLTY.NS", "Oberoi Realty",  "NSE",    "INR", "Real Estate"),
        new Asset("PRESTIGE.NS",   "Prestige",       "NSE",    "INR", "Real Estate"),
        new Asset("PHOENIXLTD.NS", "Phoenix Mills",  "NSE",    "INR", "Real Estate")
    );

    // ── Default backtest settings ─────────────────────────────────
    public static final double INITIAL_CAPITAL = 100000;
    public static final double COMMISSION_PCT = 0.0;
    public static final double SLIPPAGE = 0.0;
    public static final String TRADE_FROM = "2018-01-01";
    public static final String WARMUP_START = "2010-01-01";

    // ── Multiple-testing correction (used by SectorDsrAnalyzer) ──
    // Number of strategy variants the 16 sector winners were picked from. Used
    // by the Deflated Sharpe Ratio to discount Sharpes for selection bias.
    public static final int N_TRIALS = 4900;
    // Variance of estimated Sharpe across the trial population. We don't keep
    // the full 4,900-variant Sharpe table on disk, so we estimate this from
    // the per-stock Sharpes of each sector winner (lower bound — true cross-
    // trial variance is typically higher than within-strategy variance, so
    // our DSR estimate is *optimistic*). 0.5 is the conservative override.
    public static final double VAR_SHARPE_DEFAULT = 0.5;

    // ── Out-of-sample validation windows (used by SectorOosValidator) ──
    // The 16 sector winners are selected on the 2018-onwards window. We test
    // them against two independent held-out slices to catch different failure
    // modes:
    //   Phase 1 — Historical OOS (pre-IS, 2012-2017): catches curve-fitting,
    //   strategies that just memorized the 2018-2026 window will fail here.
    //   Phase 2 — Recent OOS (last ~2 years): catches regime drift, strategies
    //   that worked 2018-2023 but stopped working in 2024+ will fail here.
    public static final String OOS_TRADE_FROM = "2012-01-01";   // Phase 1 OOS start
    public static final String OOS_TRADE_TO   = "2017-12-31";   // Phase 1 OOS end
    public static final String IS_EARLY_TO    = "2023-12-31";   // Phase 2 IS sub-window end (paired with TRADE_FROM)
    public static final String OOS_RECENT_FROM = "2024-01-01";  // Phase 2 OOS start (runs to today)
    // Minimum bars inside an OOS window required to include a stock in the
    // aggregate (~1 trading year). Late IPOs that fall short are auto-skipped.
    public static final int    OOS_MIN_WINDOW_BARS = 252;

    // ── Rankings output ───────────────────────────────────────────
    public static final String RANKINGS_FILE = "rankings_all.xlsx";

    /**
     * Load all stocks from nifty500.txt that have cached CSV data.
     * Returns Asset objects with symbol only (name/exchange/currency/industry inferred).
     */
    public static List<Asset> loadAllStocks() {
        List<Asset> assets = new ArrayList<>();
        String stocksFile = System.getProperty("stocks.file", "nifty500.txt");
        try (InputStream is = BacktestConfig.class.getClassLoader().getResourceAsStream(stocksFile)) {
            if (is == null) {
                System.err.println("  ⚠ nifty500.txt not found on classpath");
                return assets;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                String symbol = line.trim();
                if (symbol.isEmpty()) continue;
                // Check if cache file exists
                String safeName = symbol.replace(".", "_").replace("&", "_");
                String filename = safeName + "_1d.csv";
                Path cachePath = findCacheDir().resolve(filename);
                if (Files.exists(cachePath) && Files.size(cachePath) > 100) {
                    String exchange = symbol.endsWith(".NS") ? "NSE" : "OTHER";
                    String currency = symbol.endsWith(".NS") ? "INR" : "USD";
                    assets.add(new Asset(symbol, symbol.replace(".NS", ""), exchange, currency, "General"));
                }
            }
        } catch (IOException e) {
            System.err.println("  ⚠ Error reading nifty500.txt: " + e.getMessage());
        }
        return assets;
    }

    private static Path findCacheDir() {
        Path[] candidates = {
            Paths.get("strategies", "data", "cache"),
            Paths.get("..", "strategies", "data", "cache"),
            Paths.get("data", "cache"),
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) return p;
        }
        return Paths.get("strategies", "data", "cache");
    }

    // ── Derived ───────────────────────────────────────────────────
    public static double tradingYears() {
        int tradeYear = Integer.parseInt(TRADE_FROM.substring(0, 4));
        // Calculate from trade_from to roughly now (use 2025 as current)
        return java.time.LocalDate.now().getYear() - tradeYear +
               (java.time.LocalDate.now().getDayOfYear() / 365.0);
    }
}
