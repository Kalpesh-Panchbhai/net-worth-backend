package com.networth.networthbackend.mf;

import com.networth.networthbackend.mf.MfMetricKey.Horizon;
import com.networth.networthbackend.mf.MfMetricKey.Metric;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Read side of the mutual-fund analyser.
 * <p>
 * The leaderboard and fund-detail reads are served straight out of the precomputed
 * {@code mf_metric}/{@code mf_rank} tables (see {@link MfMetricsRecomputeService}), which is one
 * indexed read rather than loading every peer's NAV series. When those tables are empty — a fresh
 * deployment before the first nightly recompute, or a metric/horizon the precompute did not cover —
 * it falls back to computing off {@code mf_nav} at request time so the endpoint is still correct,
 * just slower. The maths for both paths lives in {@link MfMetricEngine} / {@link MfReturns}.
 */
@Slf4j
public class MfAnalysisService {

    private final MfSchemeRepository schemeRepository;
    private final MfNavRepository navRepository;
    private final MfMetricRepository metricRepository;

    public MfAnalysisService() {
        this(new MfSchemeRepository(), new MfNavRepository(), new MfMetricRepository());
    }

    public MfAnalysisService(MfSchemeRepository schemeRepository, MfNavRepository navRepository,
                             MfMetricRepository metricRepository) {
        this.schemeRepository = schemeRepository;
        this.navRepository = navRepository;
        this.metricRepository = metricRepository;
    }

    // ---- Public API ----

    /** The peer groups a leaderboard can be requested for, biggest first within each asset class. */
    public List<MfCategoryCount> categories() throws SQLException {
        return schemeRepository.categoryCounts();
    }

    /**
     * Ranks the live funds in one sub-category by a single metric over a horizon.
     * <p>
     * Served from {@code mf_rank}/{@code mf_metric} when they hold rows for the combination; falls
     * back to a live NAV computation only when they do not. Funds the metric cannot be computed for
     * are dropped from the ranking but still counted in {@code peerCount}, so a "3 of 35" reads
     * honestly.
     */
    public Leaderboard leaderboard(String subCategory, String metricCode, String horizonCode, int limit)
            throws SQLException {
        Metric metric = MfMetricKey.metric(metricCode);
        Horizon horizon = MfMetricKey.horizon(horizonCode);

        if (metricRepository.latestAsOf() != null) {
            List<MfMetricRepository.LeaderboardRow> rows =
                    metricRepository.topInCategoryDetailed(subCategory, horizon, metric, limit);
            if (!rows.isEmpty()) {
                List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
                for (MfMetricRepository.LeaderboardRow r : rows) {
                    entries.add(new LeaderboardEntry(r.rank(), r.schemeCode(), r.name(), r.amc(),
                            r.value(), r.launchDate(), r.lastNavDate()));
                }
                int peerCount = rows.get(0).peerCount();
                // Fewer rows than asked for means we saw every rankable fund, so the returned count
                // is the honest "of N" — only a full page needs a second query to learn the total.
                int ranked = rows.size() < limit
                        ? rows.size()
                        : metricRepository.countRanked(subCategory, horizon, metric);
                return new Leaderboard(subCategory, metric.code, horizon.code, metric.higherIsBetter(),
                        peerCount, ranked, entries);
            }
            // Tables are populated but hold nothing for this combination — fall through to a live
            // compute rather than reporting an empty board, so a metric/horizon added to the UI but
            // not yet to the precompute matrix still answers correctly.
        }
        return leaderboardLive(subCategory, metric, horizon, limit);
    }

    /**
     * Every headline metric for one fund. Served from {@code mf_metric} plus a cheap NAV coverage
     * lookup; falls back to a live computation when the fund has no precomputed rows.
     * Returns null if the scheme code is unknown or has no NAV.
     */
    public FundDetail fund(int schemeCode) throws SQLException {
        MfScheme scheme = schemeRepository.findByCode(schemeCode).orElse(null);
        if (scheme == null) return null;

        if (metricRepository.latestAsOf() != null) {
            Map<Horizon, Map<Metric, Double>> stored = metricRepository.loadForScheme(schemeCode);
            MfNavRepository.Coverage coverage = navRepository.coverageFor(schemeCode);
            if (!stored.isEmpty() && coverage != null) {
                List<FundMetric> metrics = new ArrayList<>();
                for (Map.Entry<Horizon, Map<Metric, Double>> byHorizon : stored.entrySet()) {
                    for (Map.Entry<Metric, Double> byMetric : byHorizon.getValue().entrySet()) {
                        metrics.add(new FundMetric(byHorizon.getKey().code, byMetric.getKey().code,
                                byMetric.getValue()));
                    }
                }
                return new FundDetail(scheme.schemeCode(), scheme.name(), scheme.amc(),
                        scheme.assetClass().name(), scheme.subCategory(), scheme.launchDate(),
                        coverage.from(), coverage.to(), coverage.points(), metrics);
            }
        }
        return fundLive(scheme, schemeCode);
    }

    /**
     * The metrics shown as columns in the browse table — the same set the leaderboard's metric
     * picker offers, so "sort by CAGR / Sharpe / …" works against the same numbers.
     */
    private static final List<Metric> TABLE_METRICS = List.of(
            Metric.CAGR, Metric.ROLLING_AVG, Metric.ROLLING_WORST, Metric.ROLLING_SHARE_ABOVE_12,
            Metric.VOLATILITY, Metric.SHARPE, Metric.SORTINO, Metric.MAX_DRAWDOWN);

    /**
     * Every fund (optionally scoped to one sub-category) with all the column metrics at one horizon,
     * for the sortable browse table. Read straight from the precomputed tables; the client does the
     * sorting and paging. A blank {@code subCategory} spans all live funds.
     */
    public Table table(String subCategory, String horizonCode) throws SQLException {
        Horizon horizon = MfMetricKey.horizon(horizonCode);
        String scope = (subCategory == null || subCategory.isBlank()) ? null : subCategory;

        List<MfMetricRepository.TableMetricRow> flat = metricRepository.tableRows(scope, horizon, TABLE_METRICS);

        // Group the (fund, metric, value) rows into one row per fund, preserving first-seen order.
        LinkedHashMap<Integer, TableRow> byScheme = new LinkedHashMap<>();
        for (MfMetricRepository.TableMetricRow r : flat) {
            TableRow row = byScheme.computeIfAbsent(r.schemeCode(), k -> new TableRow(
                    r.schemeCode(), r.name(), r.amc(), r.assetClass(), r.subCategory(), new LinkedHashMap<>()));
            row.values().put(r.metric(), r.value());
        }

        List<String> metricCodes = TABLE_METRICS.stream().map(m -> m.code).toList();
        return new Table(horizon.code, metricCodes, new ArrayList<>(byScheme.values()));
    }

    // ---- Live fallbacks: compute straight off mf_nav ----

    /**
     * Ranks a category by loading every peer's NAV series and computing the metric on the fly. Used
     * only when the precomputed tables cannot answer; it reads ~one category's worth of NAV rows.
     */
    private Leaderboard leaderboardLive(String subCategory, Metric metric, Horizon horizon, int limit)
            throws SQLException {
        List<MfScheme> peers = schemeRepository.findLiveBySubCategory(subCategory);
        List<Integer> codes = peers.stream().map(MfScheme::schemeCode).toList();
        Map<Integer, TreeMap<LocalDate, BigDecimal>> seriesByCode =
                navRepository.loadSeriesForSchemes(codes, null, null);

        List<LeaderboardEntry> scored = new ArrayList<>();
        for (MfScheme s : peers) {
            NavigableMap<LocalDate, Double> series = MfMetricEngine.toDoubleSeries(seriesByCode.get(s.schemeCode()));
            if (series == null) continue;
            Double value = MfMetricEngine.computeMetric(series, metric, horizon);
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
     * The fund-detail metrics computed live from the NAV series. Used only as a fallback, and over
     * the same (horizon, metric) matrix the precompute stores, so the detail view is identical
     * whichever path serves it.
     */
    private FundDetail fundLive(MfScheme scheme, int schemeCode) throws SQLException {
        TreeMap<LocalDate, BigDecimal> raw = navRepository.loadSeries(schemeCode, null, null);
        NavigableMap<LocalDate, Double> series = MfMetricEngine.toDoubleSeries(raw);
        if (series == null) return null;

        List<FundMetric> metrics = new ArrayList<>();
        for (MfMetricEngine.Pair p : MfMetricEngine.precomputePairs()) {
            Double value = MfMetricEngine.computeMetric(series, p.metric(), p.horizon());
            if (value == null || value.isNaN() || value.isInfinite()) continue;
            metrics.add(new FundMetric(p.horizon().code, p.metric().code, value));
        }

        return new FundDetail(scheme.schemeCode(), scheme.name(), scheme.amc(),
                scheme.assetClass().name(), scheme.subCategory(), scheme.launchDate(),
                series.firstKey(), series.lastKey(), series.size(), metrics);
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

    /** One fund's row in the browse table: its identity plus a metric-code → value map. */
    public record TableRow(int schemeCode, String name, String amc, String assetClass, String subCategory,
                           Map<String, Double> values) {
    }

    /** The browse table at one horizon: the metric columns present, and one row per fund. */
    public record Table(String horizon, List<String> metrics, List<TableRow> rows) {
    }
}
