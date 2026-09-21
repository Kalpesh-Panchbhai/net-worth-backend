package com.networth.networthbackend.mf;

import com.networth.networthbackend.mf.MfMetricKey.Horizon;
import com.networth.networthbackend.mf.MfMetricKey.Metric;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Runtime mutual-fund analysis, computed straight off {@code mf_nav} on each request rather than
 * from the (as-yet unpopulated) {@code mf_metric}/{@code mf_rank} tables.
 * <p>
 * The maths lives in {@link MfReturns}; this class only decides which window a request means, reads
 * the NAV series it needs, and shapes the answer. It is fast enough for a single fund or a single
 * category (a category is ~35 funds, read in one {@code IN (...)} query) but deliberately does not
 * offer a cross-category "rank everything" call: that reads millions of NAV rows and is the one job
 * that belongs in a precompute, not a request.
 */
@Slf4j
public class MfAnalysisService {

    /**
     * Indian risk-free proxy for Sharpe/Sortino, ~10-year G-sec. A single constant on purpose: the
     * runtime ranking only needs funds ordered consistently, and threading a live rate through every
     * request would change ranks day to day for a reason that has nothing to do with the funds.
     */
    private static final double RISK_FREE_RATE = 0.065;

    /**
     * Step between rolling windows. Monthly rather than daily: over a 13-year series a daily step is
     * ~2,000 windows per fund with a distribution indistinguishable from the ~150 a monthly step
     * gives, at a fraction of the arithmetic when ranking a whole category live.
     */
    private static final int ROLLING_STEP_DAYS = 30;

    private final MfSchemeRepository schemeRepository;
    private final MfNavRepository navRepository;

    public MfAnalysisService() {
        this(new MfSchemeRepository(), new MfNavRepository());
    }

    public MfAnalysisService(MfSchemeRepository schemeRepository, MfNavRepository navRepository) {
        this.schemeRepository = schemeRepository;
        this.navRepository = navRepository;
    }

    // ---- Public API ----

    /** The peer groups a leaderboard can be requested for, biggest first within each asset class. */
    public List<MfCategoryCount> categories() throws SQLException {
        return schemeRepository.categoryCounts();
    }

    /**
     * Ranks the live funds in one sub-category by a single metric over a horizon.
     * <p>
     * Sort direction comes from {@link Metric#higherIsBetter()}, so "best" means best for that
     * metric (top return, but lowest volatility). Funds the metric cannot be computed for — too
     * short a history for the horizon, say — are dropped from the ranking but still counted in
     * {@code peerCount}, so a "3 of 35" reads honestly.
     */
    public Leaderboard leaderboard(String subCategory, String metricCode, String horizonCode, int limit)
            throws SQLException {
        Metric metric = MfMetricKey.metric(metricCode);
        Horizon horizon = MfMetricKey.horizon(horizonCode);

        List<MfScheme> peers = schemeRepository.findLiveBySubCategory(subCategory);
        List<Integer> codes = peers.stream().map(MfScheme::schemeCode).toList();
        Map<Integer, TreeMap<LocalDate, BigDecimal>> seriesByCode =
                navRepository.loadSeriesForSchemes(codes, null, null);

        List<LeaderboardEntry> scored = new ArrayList<>();
        for (MfScheme s : peers) {
            NavigableMap<LocalDate, Double> series = toDoubleSeries(seriesByCode.get(s.schemeCode()));
            if (series == null) continue;
            Double value = computeMetric(series, metric, horizon);
            if (value == null || value.isNaN() || value.isInfinite()) continue;
            scored.add(new LeaderboardEntry(0, s.schemeCode(), s.name(), s.amc(), value,
                    s.launchDate(), s.lastNavDate()));
        }

        Comparator<LeaderboardEntry> byValue = Comparator.comparingDouble(LeaderboardEntry::value);
        scored.sort(metric.higherIsBetter() ? byValue.reversed() : byValue);

        List<LeaderboardEntry> ranked = new ArrayList<>();
        int max = limit > 0 ? Math.min(limit, scored.size()) : scored.size();
        for (int i = 0; i < max; i++) {
            LeaderboardEntry e = scored.get(i);
            ranked.add(new LeaderboardEntry(i + 1, e.schemeCode(), e.name(), e.amc(), e.value(),
                    e.launchDate(), e.lastNavDate()));
        }

        return new Leaderboard(subCategory, metric.code, horizon.code, metric.higherIsBetter(),
                peers.size(), scored.size(), ranked);
    }

    /**
     * Every headline metric for one fund: CAGR across the standard horizons, the 5-year rolling
     * distribution, and the risk measures. Returns null if the scheme code is unknown or has no NAV.
     */
    public FundDetail fund(int schemeCode) throws SQLException {
        MfScheme scheme = schemeRepository.findByCode(schemeCode).orElse(null);
        if (scheme == null) return null;

        TreeMap<LocalDate, BigDecimal> raw = navRepository.loadSeries(schemeCode, null, null);
        NavigableMap<LocalDate, Double> series = toDoubleSeries(raw);
        if (series == null) return null;

        List<FundMetric> metrics = new ArrayList<>();

        // Trailing CAGR at each standard horizon plus since inception.
        for (Horizon h : List.of(Horizon.Y1, Horizon.Y3, Horizon.Y5, Horizon.Y7, Horizon.Y10, Horizon.SI)) {
            add(metrics, h, Metric.CAGR, computeMetric(series, Metric.CAGR, h));
        }
        // Short-window absolute returns, where annualising would mislead.
        for (Horizon h : List.of(Horizon.M1, Horizon.M3, Horizon.M6)) {
            add(metrics, h, Metric.ABSOLUTE, computeMetric(series, Metric.ABSOLUTE, h));
        }
        // 5-year rolling distribution: the honest "what did investors actually get" view.
        MfReturns.Rolling rolling = MfReturns.rolling(series, Horizon.Y5.years, ROLLING_STEP_DAYS);
        if (rolling != null) {
            add(metrics, Horizon.Y5, Metric.ROLLING_AVG, rolling.average());
            add(metrics, Horizon.Y5, Metric.ROLLING_WORST, rolling.worst());
            add(metrics, Horizon.Y5, Metric.ROLLING_BEST, rolling.best());
            add(metrics, Horizon.Y5, Metric.ROLLING_SHARE_NEGATIVE, rolling.shareNegative());
            add(metrics, Horizon.Y5, Metric.ROLLING_SHARE_ABOVE_12, rolling.shareAbove12());
            add(metrics, Horizon.Y5, Metric.ROLLING_WINDOWS, (double) rolling.windows());
        }
        // Risk, measured over a trailing 3 years, plus whole-history drawdowns.
        add(metrics, Horizon.Y3, Metric.VOLATILITY, computeMetric(series, Metric.VOLATILITY, Horizon.Y3));
        add(metrics, Horizon.Y3, Metric.SHARPE, computeMetric(series, Metric.SHARPE, Horizon.Y3));
        add(metrics, Horizon.Y3, Metric.SORTINO, computeMetric(series, Metric.SORTINO, Horizon.Y3));
        add(metrics, Horizon.SI, Metric.MAX_DRAWDOWN, computeMetric(series, Metric.MAX_DRAWDOWN, Horizon.SI));
        add(metrics, Horizon.SI, Metric.CURRENT_DRAWDOWN, computeMetric(series, Metric.CURRENT_DRAWDOWN, Horizon.SI));

        return new FundDetail(scheme.schemeCode(), scheme.name(), scheme.amc(),
                scheme.assetClass().name(), scheme.subCategory(), scheme.launchDate(),
                series.firstKey(), series.lastKey(), series.size(), metrics);
    }

    // ---- Metric engine ----

    /**
     * One metric over one horizon, or null when the pair is not computable (too short a series, or a
     * combination that has no meaning — e.g. a rolling window over a sub-year horizon).
     */
    private Double computeMetric(NavigableMap<LocalDate, Double> series, Metric metric, Horizon horizon) {
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

    private Double rollingField(NavigableMap<LocalDate, Double> series, Horizon horizon, Metric metric) {
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
    private static NavigableMap<LocalDate, Double> toDoubleSeries(TreeMap<LocalDate, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) return null;
        TreeMap<LocalDate, Double> out = new TreeMap<>();
        for (Map.Entry<LocalDate, BigDecimal> e : raw.entrySet()) {
            out.put(e.getKey(), e.getValue().doubleValue());
        }
        return out;
    }

    private static void add(List<FundMetric> metrics, Horizon horizon, Metric metric, Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return;
        metrics.add(new FundMetric(horizon.code, metric.code, value));
    }

    // ---- Response shapes ----

    /** One fund's standing on a category leaderboard. {@code value} is a fraction (0.12 = 12%). */
    public record LeaderboardEntry(int rank, int schemeCode, String name, String amc, double value,
                                   LocalDate launchDate, LocalDate lastNavDate) {
    }

    /**
     * A category ranked on one metric over one horizon.
     *
     * @param peerCount live funds in the sub-category
     * @param ranked    how many of them the metric was computable for
     */
    public record Leaderboard(String subCategory, String metric, String horizon, boolean higherIsBetter,
                              int peerCount, int ranked, List<LeaderboardEntry> entries) {
    }

    /** One (horizon, metric, value) for the fund-detail view. */
    public record FundMetric(String horizon, String metric, double value) {
    }

    /** Everything computed for a single fund. */
    public record FundDetail(int schemeCode, String name, String amc, String assetClass, String subCategory,
                             LocalDate launchDate, LocalDate firstNavDate, LocalDate lastNavDate,
                             int navPoints, List<FundMetric> metrics) {
    }
}
