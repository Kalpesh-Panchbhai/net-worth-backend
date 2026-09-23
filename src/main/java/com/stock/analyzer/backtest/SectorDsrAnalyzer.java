package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.ranking.DeflatedSharpe;
import com.stock.analyzer.backtest.strategy.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Phase 2.5 — Deflated Sharpe Ratio analyser for the 16 sector winners.
 *
 * <p>The sector winners were picked by ranking ~4,900 variants per sector.
 * The raw Sharpe of the winner is upward-biased by that search: with enough
 * trials a noisy strategy will look good by chance. The Deflated Sharpe Ratio
 * (Bailey &amp; López de Prado, 2014) corrects for this — it returns the
 * probability that the observed Sharpe represents real skill rather than
 * search luck, given:
 *   • the number of trials N (4,900);
 *   • the variance of estimated Sharpe across those trials (V);
 *   • the sample length T (return observations);
 *   • the skew and kurtosis of returns.
 *
 * <p>Per sector we:
 *   1. Run the winning strategy on each sector stock (IS_FULL window) and
 *      collect daily equity-curve returns;
 *   2. Build an equal-weighted portfolio return series (intra-sector
 *      diversification: at each date, average across stocks with data);
 *   3. Compute the portfolio's annualised Sharpe, skew, kurtosis, T;
 *   4. Estimate V as the variance of the per-stock Sharpes (a proxy — see
 *      caveat in {@link BacktestConfig#VAR_SHARPE_DEFAULT});
 *   5. Compute PSR (probability true Sharpe &gt; 0) and DSR (probability the
 *      observed Sharpe survives the multiple-testing penalty).
 *
 * <p>Verdict:
 *   REAL          DSR ≥ 0.95  — statistically significant after correction
 *   MARGINAL      0.50 ≤ DSR &lt; 0.95
 *   LIKELY_NOISE  DSR &lt; 0.50
 *
 * <p>Output: {@code sector_csv_oos/dsr_summary.csv}
 */
public class SectorDsrAnalyzer {

    // MDD-priority winners with minCAGR ≥ 8% floor across every window.
    private static final String[][] SECTORS = {
        {"capital_goods",                 "R33:TONLY_ADX30_TR3_A7"},
        {"power",                         "R33:TONLY_ADX25_TR3_A7"},
        {"information_technology",        "R34:TONLY_ADX33_TR4.0_A8"},
        {"automobile_and_auto_components","R34:TONLY_ADX28_TR5.0_A8"},
        {"fast_moving_consumer_goods",    "R33:TONLY_ADX20_TR3_A7"},
        {"consumer_durables",             "R34:TONLY_ADX33_TR4.0_A8"},
        {"financial_services",            "R34:TONLY_ADX28_TR4.0_A8"},
        {"healthcare",                    "R34:TONLY_ADX30_TR4.0_A8"},
        {"chemicals",                     "R33:TONLY_ADX20_TR3_A7"},
        {"consumer_services",             "R34:TONLY_ADX33_TR4.5_A7"},
        {"realty",                        "R33:PB_E15_TR0.3_A3"},
        {"metals_and_mining",             "R33:TONLY_ADX20_TR3_A7"},
        {"services",                      "R34:TONLY_ADX30_TR5.0_A8"},
        {"construction",                  "AS7:RSI35_MT7"},
        {"oil_gas_and_consumable_fuels",  "R33:TONLY_ADX30_TR3_A7"},
        {"telecommunication",             "R33:TONLY_ADX30_TR3_A7"},
    };

    public static void main(String[] args) throws Exception {
        printSanityChecks();

        Path outDir = Paths.get("sector_csv_oos");
        Files.createDirectories(outDir);

        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();
        LocalDate isFrom = LocalDate.parse(BacktestConfig.TRADE_FROM);

        int nTrials = BacktestConfig.N_TRIALS;
        double[] varCandidates = { BacktestConfig.VAR_SHARPE_DEFAULT, 1.0 };

        System.out.printf("%n  Multiple-testing config: N_TRIALS=%d, V[SR_n] tested at %s%n",
            nTrials, Arrays.toString(varCandidates));
        System.out.printf("  IS window: %s → today%n%n", BacktestConfig.TRADE_FROM);

        System.out.println("  " + "-".repeat(135));
        System.out.printf("  %-32s %-26s %8s %8s %7s %7s %7s %8s %8s   %s%n",
            "Sector", "Strategy", "PortSR", "Skew", "Kurt", "T", "V_est",
            "PSR(0)", "DSR(0.5)", "Verdict@V=0.5");
        System.out.println("  " + "-".repeat(135));

        try (BufferedWriter summary = Files.newBufferedWriter(outDir.resolve("dsr_summary.csv"))) {
            summary.write("Sector,Strategy,Stocks,PortfolioSharpe,Skew,Kurtosis,Observations,"
                + "VarSharpeEstimated,ExpectedMaxSR_V=Vest,ExpectedMaxSR_V=0.5,ExpectedMaxSR_V=1.0,"
                + "PSR_threshold0,DSR_Vest,DSR_V=0.5,DSR_V=1.0,Verdict_Vest,Verdict_V=0.5");
            summary.newLine();

            for (String[] info : SECTORS) {
                String sectorName = info[0];
                String stratName  = info[1];

                Supplier<Strategy> factory = registry.get(stratName);
                if (factory == null) {
                    System.err.printf("  ✗ Strategy not found: %s%n", stratName);
                    continue;
                }

                Path sectorFile = Paths.get("src/main/resources/sectors/" + sectorName + ".txt");
                if (!Files.exists(sectorFile)) continue;

                List<String> symbols = Files.readAllLines(sectorFile);
                symbols.removeIf(s -> s == null || s.trim().isEmpty());

                TreeMap<LocalDate, List<Double>> portfolioReturnsByDate = new TreeMap<>();
                List<Double> perStockSharpes = new ArrayList<>();
                int includedStocks = 0;

                for (String symbol : symbols) {
                    List<Bar> bars = fetcher.fetchData(symbol, BacktestConfig.WARMUP_START);
                    if (bars == null || bars.size() < 50) continue;

                    Strategy strategy = factory.get();
                    StrategyData data = strategy.computeIndicators(bars);
                    TradingViewEngine engine = new TradingViewEngine(
                        BacktestConfig.INITIAL_CAPITAL,
                        BacktestConfig.COMMISSION_PCT,
                        BacktestConfig.SLIPPAGE,
                        "percent_of_equity",
                        100.0,
                        0);

                    for (int i = 0; i < bars.size(); i++) {
                        Bar bar = bars.get(i);
                        engine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(),
                            bar.getLow(), bar.getClose(), i);
                        boolean active = !bar.getDate().isBefore(isFrom);
                        strategy.onBar(engine, data, i, active);
                    }
                    Bar lastBar = bars.get(bars.size() - 1);
                    engine.finalize(lastBar.getDate(), lastBar.getClose());

                    List<EquityPoint> curve = engine.getEquityCurve();
                    if (curve.size() < 30) continue;

                    // Daily equity returns, restricted to the IS window (skip warmup).
                    double[] dailyR = computeDailyReturnsInWindow(curve, isFrom);
                    if (dailyR.length < 30) continue;

                    // Per-stock annualised Sharpe (used as a V[SR_n] proxy).
                    double sharpe = DeflatedSharpe.annualisedSharpe(dailyR);
                    if (Double.isFinite(sharpe) && sharpe != 0) {
                        perStockSharpes.add(sharpe);
                    }

                    // Contribute to the equal-weighted portfolio return series.
                    int j = 0;
                    for (int k = 1; k < curve.size(); k++) {
                        EquityPoint prev = curve.get(k - 1);
                        EquityPoint cur  = curve.get(k);
                        if (cur.getDate().isBefore(isFrom)) continue;
                        if (prev.getEquity() <= 0) continue;
                        if (j >= dailyR.length) break;
                        double r = dailyR[j++];
                        portfolioReturnsByDate.computeIfAbsent(cur.getDate(), d -> new ArrayList<>())
                                              .add(r);
                    }
                    includedStocks++;
                }

                if (portfolioReturnsByDate.isEmpty() || includedStocks == 0) {
                    summary.write(String.format(Locale.US,
                        "%s,%s,0,0,0,0,0,0,0,0,0,0,0,0,0,NO_DATA,NO_DATA",
                        sectorName, stratName));
                    summary.newLine();
                    continue;
                }

                // Aggregate to equal-weighted portfolio daily returns.
                double[] portReturns = new double[portfolioReturnsByDate.size()];
                int idx = 0;
                for (List<Double> rs : portfolioReturnsByDate.values()) {
                    double sum = 0;
                    for (double r : rs) sum += r;
                    portReturns[idx++] = sum / rs.size();
                }

                double portSharpe = DeflatedSharpe.annualisedSharpe(portReturns);
                double skew       = DeflatedSharpe.skewness(portReturns);
                double kurt       = DeflatedSharpe.kurtosis(portReturns);
                int    T          = portReturns.length;

                // Estimate V[SR_n] from per-stock Sharpes (lower bound; see class doc).
                double[] phArr = perStockSharpes.stream().mapToDouble(Double::doubleValue).toArray();
                double vEstimated = phArr.length >= 2 ? DeflatedSharpe.variance(phArr)
                                                       : BacktestConfig.VAR_SHARPE_DEFAULT;

                double exMaxEst = DeflatedSharpe.expectedMaxSharpe(vEstimated, nTrials);
                double exMax05  = DeflatedSharpe.expectedMaxSharpe(0.5,         nTrials);
                double exMax10  = DeflatedSharpe.expectedMaxSharpe(1.0,         nTrials);

                double psr0     = DeflatedSharpe.psr(portSharpe, 0.0,     T, skew, kurt);
                double dsrEst   = DeflatedSharpe.psr(portSharpe, exMaxEst,T, skew, kurt);
                double dsr05    = DeflatedSharpe.psr(portSharpe, exMax05, T, skew, kurt);
                double dsr10    = DeflatedSharpe.psr(portSharpe, exMax10, T, skew, kurt);

                String verdictEst = verdict(dsrEst);
                String verdict05  = verdict(dsr05);

                summary.write(String.format(Locale.US,
                    "%s,%s,%d,%.4f,%.4f,%.4f,%d,"
                    + "%.4f,%.4f,%.4f,%.4f,"
                    + "%.4f,%.4f,%.4f,%.4f,%s,%s",
                    sectorName, stratName, includedStocks,
                    portSharpe, skew, kurt, T,
                    vEstimated, exMaxEst, exMax05, exMax10,
                    psr0, dsrEst, dsr05, dsr10,
                    verdictEst, verdict05));
                summary.newLine();

                System.out.printf("  %-32s %-26s %8.3f %8.3f %7.2f %7d %7.3f %8.3f %8.3f   %s%n",
                    sectorName, stratName,
                    portSharpe, skew, kurt, T, vEstimated,
                    psr0, dsr05, verdict05);
            }
            System.out.println("  " + "-".repeat(135));
            System.out.printf("%n  Summary CSV: %s%n", outDir.resolve("dsr_summary.csv"));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Equity-curve daily returns restricted to bars on or after {@code from}. */
    private static double[] computeDailyReturnsInWindow(List<EquityPoint> curve, LocalDate from) {
        List<Double> rs = new ArrayList<>();
        for (int i = 1; i < curve.size(); i++) {
            EquityPoint prev = curve.get(i - 1);
            EquityPoint cur  = curve.get(i);
            if (cur.getDate().isBefore(from)) continue;
            if (prev.getEquity() <= 0) continue;
            double r = cur.getEquity() / prev.getEquity() - 1.0;
            rs.add(r);
        }
        double[] arr = new double[rs.size()];
        for (int i = 0; i < rs.size(); i++) arr[i] = rs.get(i);
        return arr;
    }

    private static String verdict(double dsr) {
        if (Double.isNaN(dsr)) return "NA";
        if (dsr >= 0.95) return "REAL";
        if (dsr >= 0.50) return "MARGINAL";
        return "LIKELY_NOISE";
    }

    /** Confidence checks for the math implementation — prints once at startup. */
    private static void printSanityChecks() {
        System.out.println();
        System.out.println("  DeflatedSharpe self-check:");
        System.out.printf("    cdf(0)        = %.6f   (expected 0.500000)%n", DeflatedSharpe.cdf(0));
        System.out.printf("    cdf(1.96)     = %.6f   (expected ≈0.975)%n",   DeflatedSharpe.cdf(1.96));
        System.out.printf("    cdf(-1.96)    = %.6f   (expected ≈0.025)%n",   DeflatedSharpe.cdf(-1.96));
        System.out.printf("    invCdf(0.975) = %.6f   (expected ≈1.96)%n",    DeflatedSharpe.inverseCdf(0.975));
        System.out.printf("    E[max SR | V=1, N=1000] = %.3f   (expected ≈3.24)%n",
            DeflatedSharpe.expectedMaxSharpe(1.0, 1000));
        System.out.printf("    E[max SR | V=0.5, N=4900] = %.3f%n",
            DeflatedSharpe.expectedMaxSharpe(0.5, 4900));
    }
}
