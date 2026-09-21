package com.networth.networthbackend.mf;

import com.networth.networthbackend.mf.MfMetricKey.Horizon;
import com.networth.networthbackend.mf.MfMetricKey.Metric;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Turns a fund's NAV series into a single (horizon, metric) value, and lists the (horizon, metric)
 * pairs worth storing.
 * <p>
 * The maths itself lives in {@link MfReturns}; this class only decides which window a metric means
 * and which combinations are meaningful. It is the single place both the request path
 * ({@link MfAnalysisService}) and the nightly precompute ({@link MfMetricsRecomputeService}) reach
 * for, so a new metric is added once and both readers and the writer agree on it.
 */
@UtilityClass
public class MfMetricEngine {

    /**
     * Indian risk-free proxy for Sharpe/Sortino, ~10-year G-sec. A single constant on purpose: the
     * ranking only needs funds ordered consistently, and threading a live rate through would change
     * ranks day to day for a reason that has nothing to do with the funds.
     */
    static final double RISK_FREE_RATE = 0.065;

    /**
     * Step between rolling windows. Monthly rather than daily: over a 13-year series a daily step is
     * ~2,000 windows per fund with a distribution indistinguishable from the ~150 a monthly step
     * gives, at a fraction of the arithmetic when ranking a whole category.
     */
    static final int ROLLING_STEP_DAYS = 30;

    /** A (horizon, metric) coordinate in the precompute matrix. */
    public record Pair(Horizon horizon, Metric metric) {
    }

    /** Every year-length horizon we analyse over: 1Y, 2Y, 3Y, 4Y, 5Y, 7Y, 10Y. */
    public static final List<Horizon> YEAR_HORIZONS =
            List.of(Horizon.Y1, Horizon.Y2, Horizon.Y3, Horizon.Y4, Horizon.Y5, Horizon.Y7, Horizon.Y10);

    /** The year horizons plus since inception — for return and drawdown metrics that support SI. */
    public static final List<Horizon> YEAR_HORIZONS_WITH_SI =
            List.of(Horizon.Y1, Horizon.Y2, Horizon.Y3, Horizon.Y4, Horizon.Y5, Horizon.Y7, Horizon.Y10, Horizon.SI);

    /**
     * Every (horizon, metric) the precompute stores, a superset of what the leaderboard and the
     * fund-detail page ask for. {@link #computeMetric} returns null for a fund too short for a given
     * pair, so a pair listed here that a fund cannot support is simply skipped for that fund.
     */
    public static List<Pair> precomputePairs() {
        List<Pair> pairs = new ArrayList<>();

        // Trailing CAGR across every horizon plus since inception.
        for (Horizon h : YEAR_HORIZONS_WITH_SI) pairs.add(new Pair(h, Metric.CAGR));
        // Short-window absolute returns, where annualising would mislead, plus the year horizons and
        // since inception so an "absolute return" column exists at every horizon the UI offers.
        for (Horizon h : List.of(Horizon.M1, Horizon.M3, Horizon.M6)) pairs.add(new Pair(h, Metric.ABSOLUTE));
        for (Horizon h : YEAR_HORIZONS_WITH_SI) pairs.add(new Pair(h, Metric.ABSOLUTE));
        // The rolling distribution, at every year-length horizon it is defined for.
        for (Metric m : List.of(Metric.ROLLING_AVG, Metric.ROLLING_WORST, Metric.ROLLING_BEST,
                Metric.ROLLING_SHARE_NEGATIVE, Metric.ROLLING_SHARE_ABOVE_12, Metric.ROLLING_WINDOWS)) {
            for (Horizon h : YEAR_HORIZONS) pairs.add(new Pair(h, m));
        }
        // Trailing risk measures, at every year-length horizon.
        for (Metric m : List.of(Metric.VOLATILITY, Metric.SHARPE, Metric.SORTINO)) {
            for (Horizon h : YEAR_HORIZONS) pairs.add(new Pair(h, m));
        }
        // Drawdowns are whole-history figures, so their value is the same at every horizon; storing
        // one row per horizon means whichever horizon the UI asks for finds a value.
        for (Metric m : List.of(Metric.MAX_DRAWDOWN, Metric.CURRENT_DRAWDOWN)) {
            for (Horizon h : YEAR_HORIZONS_WITH_SI) pairs.add(new Pair(h, m));
        }
        return pairs;
    }

    /**
     * One metric over one horizon, or null when the pair is not computable (too short a series, or a
     * combination that has no meaning — e.g. a rolling window over a sub-year horizon).
     */
    public static Double computeMetric(NavigableMap<LocalDate, Double> series, Metric metric, Horizon horizon) {
        if (series.size() < 2) return null;
        LocalDate last = series.lastKey();

        return switch (metric) {
            case CAGR -> horizon == Horizon.SI
                    ? MfReturns.sinceInceptionCagr(series)
                    : MfReturns.cagr(series, startOf(series, horizon, last), last);
            case ABSOLUTE -> MfReturns.absoluteReturn(series,
                    horizon == Horizon.SI ? series.firstKey() : startOf(series, horizon, last), last);
            case ROLLING_AVG, ROLLING_WORST, ROLLING_BEST,
                 ROLLING_SHARE_NEGATIVE, ROLLING_SHARE_ABOVE_12, ROLLING_WINDOWS ->
                    rollingField(series, horizon, metric);
            case VOLATILITY -> horizon.years > 0 ? MfReturns.volatility(series, horizon.years) : null;
            case SHARPE -> horizon.years > 0
                    ? MfReturns.sharpe(MfReturns.trailingCagr(series, horizon.years),
                    MfReturns.volatility(series, horizon.years), RISK_FREE_RATE)
                    : null;
            case SORTINO -> horizon.years > 0
                    ? MfReturns.sortino(series, MfReturns.trailingCagr(series, horizon.years),
                    horizon.years, RISK_FREE_RATE)
                    : null;
            case MAX_DRAWDOWN -> MfReturns.maxDrawdown(series);
            case CURRENT_DRAWDOWN -> MfReturns.currentDrawdown(series);
        };
    }

    private static Double rollingField(NavigableMap<LocalDate, Double> series, Horizon horizon, Metric metric) {
        if (horizon.years <= 0) return null;
        MfReturns.Rolling r = MfReturns.rolling(series, horizon.years, ROLLING_STEP_DAYS);
        if (r == null) return null;
        return switch (metric) {
            case ROLLING_AVG -> r.average();
            case ROLLING_WORST -> r.worst();
            case ROLLING_BEST -> r.best();
            case ROLLING_SHARE_NEGATIVE -> r.shareNegative();
            case ROLLING_SHARE_ABOVE_12 -> r.shareAbove12();
            case ROLLING_WINDOWS -> (double) r.windows();
            default -> null;
        };
    }

    /** Window start for a horizon, measured back from the series' last NAV date. */
    private static LocalDate startOf(NavigableMap<LocalDate, Double> series, Horizon horizon, LocalDate last) {
        return switch (horizon) {
            case M1 -> last.minusMonths(1);
            case M3 -> last.minusMonths(3);
            case M6 -> last.minusMonths(6);
            case SI -> series.firstKey();
            default -> last.minusYears(horizon.years);
        };
    }

    /** BigDecimal NAVs to primitive doubles once, so the maths loops stay in the cheap type. */
    public static NavigableMap<LocalDate, Double> toDoubleSeries(TreeMap<LocalDate, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) return null;
        TreeMap<LocalDate, Double> out = new TreeMap<>();
        for (Map.Entry<LocalDate, BigDecimal> e : raw.entrySet()) {
            out.put(e.getKey(), e.getValue().doubleValue());
        }
        return out;
    }
}
