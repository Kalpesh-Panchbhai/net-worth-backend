package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.Market;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.ranking.DeflatedSharpe;
import com.stock.analyzer.backtest.ranking.Scorer;
import com.stock.analyzer.backtest.ranking.SubWindowMetrics;
import com.stock.analyzer.backtest.strategy.*;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * Picks the per-sector strategy that is best across <b>all three validation
 * phases simultaneously</b>: Historical OOS robustness (Phase 1), Recent OOS
 * stability (Phase 2), and Deflated Sharpe Ratio (Phase 2.5).
 *
 * <p>Workflow:
 *   1. For each sector, read the top-K variants from {@code sector_results_final/{sector}.xlsx}
 *      (the original 4,900-variant search, IS_FULL Score column).
 *   2. For each top-K variant, run a <b>single</b> continuous backtest per
 *      sector stock starting at {@code OOS_TRADE_FROM} (2012-01-01). This one
 *      run covers all four windows; we slice the equity curve + trade list
 *      to produce per-window metrics via {@link SubWindowMetrics}.
 *   3. Aggregate per window via {@link Scorer}; compute DSR via the IS_FULL
 *      portfolio return series.
 *   4. Compute a composite score that rewards <i>worst-case</i> CAGR across
 *      windows, low MDD, high PF/WR, and high DSR, with hard filters that
 *      disqualify any variant failing any window's basic profitability.
 *   5. Pick the highest composite per sector. Output winners, scoreboard
 *      (top 10 per sector), and a "vs old winner" comparison CSV.
 *
 * <p>The single-continuous-run trade-off: a position open at end-2017 carries
 * into 2018, slightly mixing window state. For ranking purposes that bleed is
 * a rounding error; the final winners must still pass the strict 4-separate-run
 * verification in {@link SectorOosValidator} and {@link SectorDsrAnalyzer}.
 *
 * <p>Compute estimate at K=50: 16 × 50 × ~25 stocks ≈ 20,000 backtests.
 */
public class SectorAllPhasesOptimizer {

    private static final String[] SECTORS = {
        "automobile_and_auto_components", "capital_goods", "chemicals", "construction",
        "construction_materials", "consumer_durables", "consumer_services",
        "fast_moving_consumer_goods", "financial_services", "healthcare",
        "information_technology", "metals_and_mining", "oil_gas_and_consumable_fuels",
        "power", "realty", "services", "telecommunication", "textiles"
    };

    // Currently deployed per-sector winners — copied exactly from LiveSignalScanner.SECTOR_WINNERS.
    // Deployed R40 hybrid (11 NEW + 7 OLD kept + 2 new sectors)
    private static final Map<String, String> OLD_WINNERS = new LinkedHashMap<>();
    static {
        OLD_WINNERS.put("automobile_and_auto_components","R40:TONLY_ADX29_TR5_W20");
        OLD_WINNERS.put("capital_goods",                 "R40:PB_ADX13_CD20_TR5_A5_TP30");
        OLD_WINNERS.put("chemicals",                     "R40:TONLY_ADX27_TR4_W30_TP10");
        OLD_WINNERS.put("construction",                  "R40:RSI50_ADX25_TR3_W25_TP15");
        OLD_WINNERS.put("construction_materials",        "R34:TONLY_ADX33_TR4.5_A5");
        OLD_WINNERS.put("consumer_durables",             "R34:TONLY_ADX27_TR5.0_A6");
        OLD_WINNERS.put("consumer_services",             "R40:RSI45_ADX30_TR5_W10_TP10");
        OLD_WINNERS.put("fast_moving_consumer_goods",    "R40:TONLY_ADX27_TR4_W25_TP15");
        OLD_WINNERS.put("financial_services",            "R40:TONLY_ADX30_TR5_W35_TP15");
        OLD_WINNERS.put("healthcare",                    "R40:TONLY_ADX27_TR5_W30_TP20");
        OLD_WINNERS.put("information_technology",        "R34:TONLY_ADX28_TR5.0_A8");
        OLD_WINNERS.put("metals_and_mining",             "R40:PB_ADX20_CD50_TR5_A7_TP10");
        OLD_WINNERS.put("oil_gas_and_consumable_fuels",  "R34:TONLY_ADX27_TR5.5_A8");
        OLD_WINNERS.put("power",                         "R40:PB_ADX20_CD50_TR3_A5_TP10");
        OLD_WINNERS.put("realty",                        "R21:RSI_HEB2_VOL8");
        OLD_WINNERS.put("services",                      "HA_R30:REG_TP3");
        OLD_WINNERS.put("telecommunication",             "R40:PB12_ADX20_TR3_A7_TP10");
        OLD_WINNERS.put("textiles",                      "HA_R31:RSI20_SL15_TP10");
    }

    // Best per-sector winners from the R39 run (for comparison with R40)
    private static final Map<String, String> R39_WINNERS = new LinkedHashMap<>();
    static {
        R39_WINNERS.put("automobile_and_auto_components","R38:TONLY_ADX30_TR5_A5");
        R39_WINNERS.put("capital_goods",                 "R39:PB_ADX15_TR5_A5_TP30");
        R39_WINNERS.put("chemicals",                     "R34:TONLY_ADX31_TR4.0_A5");
        R39_WINNERS.put("construction",                  "R39:REG_W15_T80_CD30_TR5");
        R39_WINNERS.put("construction_materials",        "R38:TONLY_ADX33_TR4.5_A5_TP30");
        R39_WINNERS.put("consumer_durables",             "R38:TONLY_ADX33_TR5.0_A8_TP20");
        R39_WINNERS.put("consumer_services",             "R39:RSI45_ADX30_TR4_TP10");
        R39_WINNERS.put("fast_moving_consumer_goods",    "R39:RSI45_ADX30_TR3_W20");
        R39_WINNERS.put("financial_services",            "R39:TONLY_ADX27_TR3.0_W30");
        R39_WINNERS.put("healthcare",                    "R39:TONLY_ADX27_TR3.0_W30");
        R39_WINNERS.put("information_technology",        "R39:TONLY_ADX30_CD30_TR5_A10");
        R39_WINNERS.put("metals_and_mining",             "R39:PB_ADX15_CD50_TR3_A7");
        R39_WINNERS.put("oil_gas_and_consumable_fuels",  "R39:RSI60_ADX30_TR3_W20");
        R39_WINNERS.put("power",                         "R39:PB_ADX20_TR3_A5_TP10");
        R39_WINNERS.put("realty",                        "R39:REG_W12_T85_R50_TR5");
        R39_WINNERS.put("services",                      "R39:RSI45_ADX30_TR5_TP10");
        R39_WINNERS.put("telecommunication",             "R38:PB12_TONLY_ADX20_TR3_A7");
        R39_WINNERS.put("textiles",                      "R39:RSI50_ADX25_TR4_TP15");
    }

    /**
     * -1 = evaluate ALL variants in the registry (full search, ~30-40 min wall clock).
     * Positive = read the top-K from {@code sector_results_final/{sector}.xlsx}
     * (fast pre-filtered search, ~3 min wall clock).
     */
    private static final int TOP_K_PER_SECTOR = -1;
    private static final String OUT_DIR = "sector_all_phases";
    /** Print progress every N variants per sector. */
    private static final int PROGRESS_EVERY = 100;

    // Window definitions (sliced from one continuous run that starts at OOS_HIST_FROM).
    private static final LocalDate RUN_FROM         = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);
    private static final LocalDate OOS_HIST_FROM    = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);
    private static final LocalDate OOS_HIST_TO      = LocalDate.parse(BacktestConfig.OOS_TRADE_TO);
    private static final LocalDate IS_FULL_FROM     = LocalDate.parse(BacktestConfig.TRADE_FROM);
    private static final LocalDate IS_EARLY_TO      = LocalDate.parse(BacktestConfig.IS_EARLY_TO);
    private static final LocalDate OOS_RECENT_FROM  = LocalDate.parse(BacktestConfig.OOS_RECENT_FROM);

    public static void main(String[] args) throws Exception {
        Market market = Market.current();
        Path outDir = market.phasesDir();
        Files.createDirectories(outDir);

        List<String> sectors = market.sectors();

        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();

        String mode = TOP_K_PER_SECTOR < 0 ? "ALL " + registry.size() : "top-" + TOP_K_PER_SECTOR;
        System.out.printf("%n  Market: %s   |   Sectors: %d   |   Output: %s%n",
            market.code(), sectors.size(), outDir);
        System.out.printf("  Mode: %s variants per sector   |   Parallelism: %d threads%n",
            mode, Runtime.getRuntime().availableProcessors());
        System.out.printf("  RUN window: %s → today (sliced into 4 phase windows)%n%n", RUN_FROM);

        try (BufferedWriter winners = Files.newBufferedWriter(outDir.resolve("new_winners.csv"));
             BufferedWriter board   = Files.newBufferedWriter(outDir.resolve("top10_per_sector.csv"));
             BufferedWriter compare = Files.newBufferedWriter(outDir.resolve("comparison_old_vs_new.csv"))) {

            winners.write("Sector,Strategy,Composite,IS_Full_CAGR,IS_Early_CAGR,OOS_Hist_CAGR,OOS_Recent_CAGR,"
                + "MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest,StocksUsed");
            winners.newLine();

            board.write("Sector,Rank,Strategy,Composite,IS_Full_CAGR,IS_Early_CAGR,OOS_Hist_CAGR,OOS_Recent_CAGR,"
                + "MinCAGR,MaxMDD,MinPF,MinWR,PortSharpe,DSR_Vest");
            board.newLine();

            compare.write("Sector,"
                + "Old_Strategy,Old_Composite,Old_MinCAGR,Old_MaxMDD,Old_MinPF,Old_MinWR,Old_MeanCAGR,Old_PortSharpe,"
                + "New_Strategy,New_Composite,New_MinCAGR,New_MaxMDD,New_MinPF,New_MinWR,New_MeanCAGR,New_PortSharpe,"
                + "Improvement");
            compare.newLine();

            long t0 = System.currentTimeMillis();

            for (String sector : sectors) {
                long sectorT0 = System.currentTimeMillis();

                Path sectorFile = market.sectorsFsDir().resolve(sector + ".txt");
                if (!Files.exists(sectorFile)) {
                    System.err.printf("  [%s] ✗ sector file missing%n", sector);
                    continue;
                }
                List<String> stocks = Files.readAllLines(sectorFile);
                stocks.removeIf(s -> s == null || s.trim().isEmpty());

                // Pre-load bars once per sector so the parallel inner loop doesn't
                // hit the CSV cache 4,900× per stock. Shared read-only across threads.
                LinkedHashMap<String, List<Bar>> barsByStock = new LinkedHashMap<>();
                for (String stock : stocks) {
                    List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
                    if (bars != null && bars.size() >= 50) barsByStock.put(stock, bars);
                }

                List<String> candidates;
                if (TOP_K_PER_SECTOR < 0) {
                    candidates = new ArrayList<>(registry.keySet());
                } else {
                    candidates = readTopK(sector, TOP_K_PER_SECTOR);
                }
                String oldName = OLD_WINNERS.get(sector);
                if (oldName != null && !candidates.contains(oldName)) {
                    candidates.add(oldName);
                }

                final int total = candidates.size();
                final java.util.concurrent.atomic.AtomicInteger progress =
                    new java.util.concurrent.atomic.AtomicInteger();
                final long sectorStart = sectorT0;
                System.out.printf("  [%s] %d variants × %d stocks ...%n",
                    sector, total, barsByStock.size());

                final java.util.concurrent.atomic.AtomicInteger errors =
                    new java.util.concurrent.atomic.AtomicInteger();
                List<VariantResult> results = candidates.parallelStream()
                    .map(varName -> {
                        Supplier<Strategy> factory = registry.get(varName);
                        if (factory == null) return null;
                        VariantResult vr = null;
                        try {
                            vr = evaluate(varName, factory, barsByStock);
                        } catch (Throwable t) {
                            // One buggy variant must not poison the whole sector
                            // (5,070 candidates × 30 stocks → rare-data edge cases
                            // do surface). Swallow + count, continue.
                            if (errors.incrementAndGet() <= 3) {
                                System.err.println("  ✗ ERROR in " + varName + ": " + t);
                                t.printStackTrace(System.err);
                            }
                        }
                        int done = progress.incrementAndGet();
                        if (done % PROGRESS_EVERY == 0 || done == total) {
                            long elapsed = System.currentTimeMillis() - sectorStart;
                            double eta = total > done ? elapsed * (total - done) / (double) done / 1000.0 : 0;
                            System.out.printf("    [%s] %d/%d  err=%d  elapsed=%.0fs  eta=%.0fs%n",
                                sector, done, total, errors.get(), elapsed / 1000.0, eta);
                        }
                        return vr;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

                if (results.isEmpty()) {
                    System.out.println("  ✗ no valid results for " + sector);
                    continue;
                }

                results.sort((a, b) -> Double.compare(b.composite, a.composite));
                VariantResult best = results.get(0);

                long ms = System.currentTimeMillis() - sectorT0;
                System.out.printf("  [%s] winner=%s comp=%.3f  [%.1fs total]%n",
                    sector, best.strategyName, best.composite, ms / 1000.0);

                winners.write(formatWinnerRow(sector, best));
                winners.newLine();

                int top = Math.min(10, results.size());
                for (int i = 0; i < top; i++) {
                    board.write(String.format(Locale.US, "%s,%d,%s",
                        sector, i + 1, formatTopRow(results.get(i))));
                    board.newLine();
                }

                VariantResult oldResult = results.stream()
                    .filter(r -> r.strategyName.equals(oldName))
                    .findFirst()
                    .orElse(null);
                if (oldResult != null) {
                    double improvement = best.composite - oldResult.composite;
                    compare.write(String.format(Locale.US,

                        "%s,"
                        + "%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,"
                        + "%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,"
                        + "%+.4f",
                        sector,
                        oldName, oldResult.composite, oldResult.minCagr, oldResult.maxMdd,
                        oldResult.minPf, oldResult.minWr, oldResult.meanCagr, oldResult.portSharpe,
                        best.strategyName, best.composite, best.minCagr, best.maxMdd,
                        best.minPf, best.minWr, best.meanCagr, best.portSharpe,
                        improvement));
                    compare.newLine();
                }
            }

            long totalMs = System.currentTimeMillis() - t0;
            System.out.printf("%n  Done in %.1f min.   Output: %s%n", totalMs / 60000.0, outDir);
            System.out.printf("  Files: new_winners.csv  top10_per_sector.csv  comparison_old_vs_new.csv%n");
        }
    }

    // ── Per-variant evaluation (4 independent backtests per stock) ────

    /** Window definition: tradeFrom → tradeTo (null = open-ended to today). */
    private static final LocalDate[][] WINDOWS = {
        { IS_FULL_FROM,    null          },  // 0 = IS_FULL
        { IS_FULL_FROM,    IS_EARLY_TO   },  // 1 = IS_EARLY
        { OOS_HIST_FROM,   OOS_HIST_TO   },  // 2 = OOS_HIST
        { OOS_RECENT_FROM, null          },  // 3 = OOS_RECENT
    };

    /**
     * Run a single independent backtest for one stock in one window.
     * Fresh strategy, fresh engine, fresh ₹100K — zero bleed between windows.
     */
    static Metrics runOneWindow(Supplier<Strategy> factory, List<Bar> bars,
                                        LocalDate tradeFrom, LocalDate tradeTo) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        double cap = BacktestConfig.INITIAL_CAPITAL;
        TradingViewEngine engine = new TradingViewEngine(
            cap, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
            "percent_of_equity", 100.0, 0);

        LocalDate lastDate = null;
        double lastClose = 0;
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            // Stop processing bars beyond the window end
            if (tradeTo != null && d.isAfter(tradeTo)) break;
            engine.processBar(d, bar.getOpen(), bar.getHigh(),
                bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(tradeFrom);
            strategy.onBar(engine, data, i, active);
            lastDate = d;
            lastClose = bar.getClose();
        }
        if (lastDate == null) return null;
        engine.finalize(lastDate, lastClose);

        List<EquityPoint> curve = engine.getEquityCurve();
        List<ClosedTrade> trades = engine.getClosedTrades();
        if (curve.size() < 2) return null;

        // Compute metrics directly from the full engine output for the window
        // (trades are already constrained because the engine stopped at tradeTo).
        // Use tradeFrom as the metric start to exclude warmup equity.
        return SubWindowMetrics.slice(curve, trades, tradeFrom, tradeTo, cap);
    }

    static VariantResult evaluate(String varName, Supplier<Strategy> factory,
                                          Map<String, List<Bar>> barsByStock) {
        Map<String, Metrics> isFull   = new LinkedHashMap<>();
        Map<String, Metrics> isEarly  = new LinkedHashMap<>();
        Map<String, Metrics> oosHist  = new LinkedHashMap<>();
        Map<String, Metrics> oosRecent= new LinkedHashMap<>();

        TreeMap<LocalDate, List<Double>> portfolioReturns = new TreeMap<>();
        List<Double> perStockSharpe = new ArrayList<>();

        for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
            String stock = e.getKey();
            List<Bar> bars = e.getValue();

            // 4 independent backtests — each with fresh strategy + engine + capital
            Metrics mFull   = runOneWindow(factory, bars, IS_FULL_FROM,    null);
            Metrics mEarly  = runOneWindow(factory, bars, IS_FULL_FROM,    IS_EARLY_TO);
            Metrics mHist   = runOneWindow(factory, bars, OOS_HIST_FROM,   OOS_HIST_TO);
            Metrics mRecent = runOneWindow(factory, bars, OOS_RECENT_FROM, null);

            if (mFull   != null) isFull.put(stock,    mFull);
            if (mEarly  != null) isEarly.put(stock,   mEarly);
            if (mHist   != null) oosHist.put(stock,   mHist);
            if (mRecent != null) oosRecent.put(stock,  mRecent);

            // Portfolio daily returns from IS_FULL window for DSR.
            if (mFull != null) {
                // Re-run IS_FULL to get equity curve for DSR (reuse the same logic)
                Strategy dsrStrat = factory.get();
                StrategyData dsrData = dsrStrat.computeIndicators(bars);
                TradingViewEngine dsrEngine = new TradingViewEngine(
                    BacktestConfig.INITIAL_CAPITAL, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
                    "percent_of_equity", 100.0, 0);
                for (int i = 0; i < bars.size(); i++) {
                    Bar bar = bars.get(i);
                    dsrEngine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(),
                        bar.getLow(), bar.getClose(), i);
                    boolean active = !bar.getDate().isBefore(IS_FULL_FROM);
                    dsrStrat.onBar(dsrEngine, dsrData, i, active);
                }
                Bar last = bars.get(bars.size() - 1);
                dsrEngine.finalize(last.getDate(), last.getClose());

                EquityPoint prev = null;
                for (EquityPoint p : dsrEngine.getEquityCurve()) {
                    if (p.getDate().isBefore(IS_FULL_FROM)) { prev = p; continue; }
                    if (prev != null && prev.getEquity() > 0) {
                        double r = p.getEquity() / prev.getEquity() - 1.0;
                        portfolioReturns.computeIfAbsent(p.getDate(), k -> new ArrayList<>()).add(r);
                    }
                    prev = p;
                }
                double sharpe = mFull.getSharpeRatio();
                if (Double.isFinite(sharpe) && sharpe != 0) perStockSharpe.add(sharpe);
            }
        }

        if (isFull.isEmpty() || portfolioReturns.isEmpty()) return null;

        double isYears   = years(IS_FULL_FROM,    LocalDate.now());
        double earlYrs   = years(IS_FULL_FROM,    IS_EARLY_TO);
        double histYrs   = years(OOS_HIST_FROM,   OOS_HIST_TO);
        double recYrs    = years(OOS_RECENT_FROM, LocalDate.now());

        ScoreData sF = Scorer.computeScore(isFull,   isYears);
        ScoreData sE = Scorer.computeScore(isEarly,  earlYrs);
        ScoreData sH = Scorer.computeScore(oosHist,  histYrs);
        ScoreData sR = Scorer.computeScore(oosRecent,recYrs);

        // DSR from equal-weighted portfolio of IS_FULL daily returns.
        double[] portR = new double[portfolioReturns.size()];
        int i = 0;
        for (List<Double> day : portfolioReturns.values()) {
            double s = 0; for (double r : day) s += r;
            portR[i++] = s / day.size();
        }
        double portSharpe = DeflatedSharpe.annualisedSharpe(portR);
        double skew = DeflatedSharpe.skewness(portR);
        double kurt = DeflatedSharpe.kurtosis(portR);
        double[] phArr = perStockSharpe.stream().mapToDouble(Double::doubleValue).toArray();
        double vEst = phArr.length >= 2 ? DeflatedSharpe.variance(phArr) : BacktestConfig.VAR_SHARPE_DEFAULT;
        double dsrVest = DeflatedSharpe.dsr(portSharpe, BacktestConfig.N_TRIALS, vEst, portR.length, skew, kurt);

        VariantResult vr = new VariantResult();
        vr.strategyName = varName;
        vr.isFull = sF; vr.isEarly = sE; vr.oosHist = sH; vr.oosRecent = sR;
        vr.portSharpe = portSharpe;
        vr.dsrVest = Double.isNaN(dsrVest) ? 0 : dsrVest;
        vr.stocksUsed = isFull.size();

        double[] cagrs = { sF.getAvgCagr(), sE.getAvgCagr(), sH.getAvgCagr(), sR.getAvgCagr() };
        double[] mdds  = { Math.abs(sF.getAvgMaxDd()), Math.abs(sE.getAvgMaxDd()),
                           Math.abs(sH.getAvgMaxDd()), Math.abs(sR.getAvgMaxDd()) };
        double[] pfs   = { sF.getAvgPf(), sE.getAvgPf(), sH.getAvgPf(), sR.getAvgPf() };
        double[] wrs   = { sF.getAvgWr(), sE.getAvgWr(), sH.getAvgWr(), sR.getAvgWr() };

        vr.minCagr  = min(cagrs);
        vr.meanCagr = mean(cagrs);
        vr.maxMdd   = max(mdds);
        vr.meanMdd  = mean(mdds);
        vr.minPf    = min(pfs);
        vr.meanPf   = mean(pfs);
        vr.minWr    = min(wrs);
        vr.meanWr   = mean(wrs);

        vr.composite = composite(vr);
        return vr;
    }

    /**
     * Fast variant evaluator with early termination on hard-filter failure.
     *
     * Processes the 4 windows in order from shortest to longest. After each
     * window, checks if the aggregate metric across stocks already breaches a
     * hard filter (avg CAGR < 8%, avg PF < 1.0, avg WR < 25%, or avg MDD > 55%).
     * If so, returns a stub VariantResult with composite = -1 without running
     * the remaining windows or the expensive DSR pass.
     *
     * Speedups vs evaluate():
     *   1. Most disqualified variants short-circuit after window 1 (OOS_RECENT).
     *   2. DSR / portfolio Sharpe is skipped entirely — composite() doesn't
     *      use them, only minCagr/maxMdd/minPf/minWr.
     *
     * Returned VariantResult fields are limited to what composite() needs and
     * what FullRecheck's CSV writers consume.
     */
    static VariantResult evaluateFast(String varName, Supplier<Strategy> factory,
                                      Map<String, List<Bar>> barsByStock) {
        // Run shortest windows first so most variants fail-check quickly.
        LocalDate[][] orderedWindows = {
            { OOS_RECENT_FROM, null         },  // shortest (~2 yrs)
            { OOS_HIST_FROM,   OOS_HIST_TO  },  // 6 yrs
            { IS_FULL_FROM,    IS_EARLY_TO  },  // ~5 yrs
            { IS_FULL_FROM,    null         },  // ~8 yrs (full IS)
        };
        ScoreData[] sd = new ScoreData[4];
        Map<String, Metrics> isFullMap = null;

        for (int w = 0; w < 4; w++) {
            LocalDate from = orderedWindows[w][0];
            LocalDate to   = orderedWindows[w][1];

            Map<String, Metrics> windowMetrics = new LinkedHashMap<>();
            for (Map.Entry<String, List<Bar>> e : barsByStock.entrySet()) {
                Metrics m = runOneWindow(factory, e.getValue(), from, to);
                if (m != null) windowMetrics.put(e.getKey(), m);
            }
            if (windowMetrics.size() < MIN_STOCKS) return null;

            double yrs = years(from, to == null ? LocalDate.now() : to);
            ScoreData score = Scorer.computeScore(windowMetrics, yrs);
            sd[w] = score;
            if (from == IS_FULL_FROM && to == null) isFullMap = windowMetrics;

            double avgCagr = score.getAvgCagr();
            double avgPf   = score.getAvgPf();
            double avgWr   = score.getAvgWr();
            double avgMdd  = Math.abs(score.getAvgMaxDd());

            // Early bail — any window failing a hard filter dooms composite.
            if (avgCagr < MIN_CAGR_FLOOR
                || avgPf  < MIN_PF_FLOOR
                || avgWr  < MIN_WR_FLOOR
                || avgMdd > MAX_MDD_CEIL) {
                VariantResult bail = new VariantResult();
                bail.strategyName = varName;
                bail.composite = -1.0;
                bail.minCagr = avgCagr;
                bail.minPf   = avgPf;
                bail.minWr   = avgWr;
                bail.maxMdd  = avgMdd;
                bail.isFull  = score;       // any non-null ScoreData is fine
                bail.stocksUsed = windowMetrics.size();
                return bail;
            }
        }

        // All 4 windows pass — compute final composite (no DSR; not needed by composite).
        ScoreData sR = sd[0];
        ScoreData sH = sd[1];
        ScoreData sE = sd[2];
        ScoreData sF = sd[3];

        VariantResult vr = new VariantResult();
        vr.strategyName = varName;
        vr.isFull = sF; vr.isEarly = sE; vr.oosHist = sH; vr.oosRecent = sR;
        vr.stocksUsed = isFullMap != null ? isFullMap.size() : sF.getNumAssets();

        double[] cagrs = { sF.getAvgCagr(), sE.getAvgCagr(), sH.getAvgCagr(), sR.getAvgCagr() };
        double[] mdds  = { Math.abs(sF.getAvgMaxDd()), Math.abs(sE.getAvgMaxDd()),
                           Math.abs(sH.getAvgMaxDd()), Math.abs(sR.getAvgMaxDd()) };
        double[] pfs   = { sF.getAvgPf(), sE.getAvgPf(), sH.getAvgPf(), sR.getAvgPf() };
        double[] wrs   = { sF.getAvgWr(), sE.getAvgWr(), sH.getAvgWr(), sR.getAvgWr() };

        vr.minCagr  = min(cagrs);
        vr.meanCagr = mean(cagrs);
        vr.maxMdd   = max(mdds);
        vr.meanMdd  = mean(mdds);
        vr.minPf    = min(pfs);
        vr.meanPf   = mean(pfs);
        vr.minWr    = min(wrs);
        vr.meanWr   = mean(wrs);

        // DSR fields left at default zero — not used by composite(). Add a full
        // DSR rerun via evaluate() only when promoting a winner to production.
        vr.portSharpe = 0.0;
        vr.dsrVest = 0.0;
        vr.composite = composite(vr);
        return vr;
    }

    // ── Hard filter thresholds ────────────────────────────────────
    private static final double MIN_CAGR_FLOOR = 8.0;
    private static final double MIN_PF_FLOOR   = 1.0;
    private static final double MIN_WR_FLOOR   = 25.0;
    private static final double MAX_MDD_CEIL   = 55.0;
    private static final int    MIN_STOCKS     = 3;

    // ── Scoring bounds (linear scale: floor → 0, cap → 100) ─────
    private static final double CAGR_FLOOR = 8.0,  CAGR_CAP = 30.0;
    private static final double WR_FLOOR   = 25.0, WR_CAP   = 60.0;
    private static final double PF_FLOOR   = 1.0,  PF_CAP   = 5.0;
    private static final double MDD_BEST   = 10.0, MDD_WORST = 55.0;

    /**
     * Equal-weight composite using <b>worst-case</b> values across all 4 windows.
     * This forces consistency — a strategy cannot hide a weak period behind a strong one.
     *
     * <p>Hard filters (instant disqualification):
     *   CAGR  < 8%  in ANY window  |  PF < 1.0 in ANY window
     *   WR    < 25% in ANY window  |  MDD > 55% in ANY window
     *   Fewer than 3 stocks
     *
     * <p>Score = 25% × CAGR + 25% × WR + 25% × PF + 25% × MDD
     * Each pillar uses the worst value across all 4 windows, linearly scaled 0–1.
     */
    static double composite(VariantResult v) {
        boolean hardOk =
            v.minCagr >= MIN_CAGR_FLOOR &&
            v.minPf   >= MIN_PF_FLOOR   &&
            v.minWr   >= MIN_WR_FLOOR   &&
            v.maxMdd  <= MAX_MDD_CEIL   &&
            v.isFull.getNumAssets() >= MIN_STOCKS;
        if (!hardOk) return -1;

        // CAGR: min across 4 windows; 8% → 0, 30% → 1
        double cagrScore = clamp((v.minCagr - CAGR_FLOOR) / (CAGR_CAP - CAGR_FLOOR), 0, 1);
        // WR: min across 4 windows; 25% → 0, 60% → 1
        double wrScore   = clamp((v.minWr - WR_FLOOR) / (WR_CAP - WR_FLOOR), 0, 1);
        // PF: min across 4 windows; 1.0 → 0, 5.0 → 1
        double pfScore   = clamp((v.minPf - PF_FLOOR) / (PF_CAP - PF_FLOOR), 0, 1);
        // MDD: max (worst) across 4 windows; 10% → 1, 55% → 0
        double mddScore  = clamp((MDD_WORST - v.maxMdd) / (MDD_WORST - MDD_BEST), 0, 1);

        return 0.25 * cagrScore
             + 0.25 * wrScore
             + 0.25 * pfScore
             + 0.25 * mddScore;
    }

    // ── XLSX reader ──────────────────────────────────────────────────

    private static List<String> readTopK(String sector, int k) throws IOException {
        Path file = Paths.get("sector_results_final", sector + ".xlsx");
        List<String> out = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum() && out.size() < k; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = row.getCell(1) != null ? row.getCell(1).getStringCellValue() : null;
                if (name != null && !name.isEmpty()) out.add(name);
            }
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    static class VariantResult {
        String strategyName;
        ScoreData isFull, isEarly, oosHist, oosRecent;
        double portSharpe, dsrVest;
        int stocksUsed;
        double minCagr, meanCagr, maxMdd, meanMdd, minPf, meanPf, minWr, meanWr;
        double composite;
    }

    private static String formatWinnerRow(String sector, VariantResult v) {
        return String.format(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%d",
            sector, v.strategyName, v.composite,
            v.isFull.getAvgCagr(), v.isEarly.getAvgCagr(),
            v.oosHist.getAvgCagr(), v.oosRecent.getAvgCagr(),
            v.minCagr, v.maxMdd, v.minPf, v.minWr,
            v.portSharpe, v.dsrVest, v.stocksUsed);
    }

    private static String formatTopRow(VariantResult v) {
        return String.format(Locale.US, "%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f",
            v.strategyName, v.composite,
            v.isFull.getAvgCagr(), v.isEarly.getAvgCagr(),
            v.oosHist.getAvgCagr(), v.oosRecent.getAvgCagr(),
            v.minCagr, v.maxMdd, v.minPf, v.minWr,
            v.portSharpe, v.dsrVest);
    }

    private static double years(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) / 365.25;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static double min(double[] a)  { double m = a[0]; for (double v : a) if (v < m) m = v; return m; }
    private static double max(double[] a)  { double m = a[0]; for (double v : a) if (v > m) m = v; return m; }
    private static double mean(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
}
