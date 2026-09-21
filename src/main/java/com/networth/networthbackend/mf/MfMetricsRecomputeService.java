package com.networth.networthbackend.mf;

import com.networth.networthbackend.mf.MfMetricEngine.Pair;
import com.networth.networthbackend.mf.MfMetricRepository.MetricRow;
import com.networth.networthbackend.mf.MfMetricRepository.RankRow;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Recomputes {@code mf_metric} and {@code mf_rank} for every live Direct+Growth fund, one category
 * at a time.
 * <p>
 * This is the "rank everything" pass the request path deliberately does not do: measured on this
 * dataset, computing the metrics is ~1s but fetching the ~2.9M NAV rows they need is the bulk of the
 * cost (~2 minutes). Doing it here nightly means a leaderboard read is later one indexed scan of
 * {@code mf_rank} instead of loading every peer's NAV series.
 * <p>
 * Runs category by category rather than loading all NAV at once, so memory stays bounded to one
 * peer group's series (~35 funds). A single {@code as_of} — the newest NAV date — is used for the
 * whole run, so the leaderboard's {@code MAX(as_of)} reads see one consistent snapshot, and a rerun
 * on the same day overwrites via the tables' {@code ON DUPLICATE KEY} rather than duplicating.
 */
@Slf4j
public class MfMetricsRecomputeService {

    private final MfSchemeRepository schemeRepository;
    private final MfNavRepository navRepository;
    private final MfMetricRepository metricRepository;

    public MfMetricsRecomputeService() {
        this(new MfSchemeRepository(), new MfNavRepository(), new MfMetricRepository());
    }

    public MfMetricsRecomputeService(MfSchemeRepository schemeRepository, MfNavRepository navRepository,
                                     MfMetricRepository metricRepository) {
        this.schemeRepository = schemeRepository;
        this.navRepository = navRepository;
        this.metricRepository = metricRepository;
    }

    /** Outcome of one recompute, for the daily job's log line. */
    public record Result(LocalDate asOf, int categories, int funds, int metricRows, int rankRows) {
        static Result empty() {
            return new Result(null, 0, 0, 0, 0);
        }
    }

    /**
     * Recomputes every metric and rank against the newest NAV date. Idempotent: safe to re-run.
     * A no-op returning {@link Result#empty()} when {@code mf_nav} is empty.
     */
    public Result recompute() throws SQLException {
        LocalDate asOf = navRepository.latestNavDate();
        if (asOf == null) {
            log.warn("MF metrics recompute skipped: mf_nav is empty");
            return Result.empty();
        }

        List<Pair> pairs = MfMetricEngine.precomputePairs();

        // Distinct sub-categories: the leaderboard and mf_rank key on sub_category alone, so a name
        // that appears under more than one asset class is one peer group, ranked once.
        LinkedHashSet<String> subCategories = new LinkedHashSet<>();
        for (MfCategoryCount c : schemeRepository.categoryCounts()) subCategories.add(c.subCategory());

        int categories = 0;
        int funds = 0;
        int metricRows = 0;
        int rankRows = 0;

        for (String subCategory : subCategories) {
            List<MfScheme> peers = schemeRepository.findLiveBySubCategory(subCategory);
            if (peers.isEmpty()) continue;

            List<Integer> codes = peers.stream().map(MfScheme::schemeCode).toList();
            Map<Integer, TreeMap<LocalDate, BigDecimal>> seriesByCode =
                    navRepository.loadSeriesForSchemes(codes, null, null);

            List<MetricRow> metrics = new ArrayList<>();
            // Per (horizon, metric), the funds that had a computable value, for ranking below.
            Map<Pair, List<Scored>> buckets = new LinkedHashMap<>();
            int peerCount = peers.size();

            for (MfScheme s : peers) {
                NavigableMap<LocalDate, Double> series =
                        MfMetricEngine.toDoubleSeries(seriesByCode.get(s.schemeCode()));
                if (series == null) continue;
                funds++;
                for (Pair p : pairs) {
                    Double value = MfMetricEngine.computeMetric(series, p.metric(), p.horizon());
                    if (value == null || value.isNaN() || value.isInfinite()) continue;
                    metrics.add(new MetricRow(s.schemeCode(), asOf, p.horizon(), p.metric(), value));
                    buckets.computeIfAbsent(p, k -> new ArrayList<>()).add(new Scored(s.schemeCode(), value));
                }
            }

            metricRows += metricRepository.upsertMetrics(metrics);
            rankRows += metricRepository.upsertRanks(rank(subCategory, asOf, buckets, peerCount));
            categories++;
        }

        int pruned = metricRepository.prune(asOf);
        log.info("MF metrics recompute done as_of {}: {} categories, {} funds, {} metric rows, {} rank rows, {} pruned",
                asOf, categories, funds, metricRows, rankRows, pruned);
        return new Result(asOf, categories, funds, metricRows, rankRows);
    }

    /** Turns each (horizon, metric) bucket into ranked rows: 1 = best, percentile 100 = best. */
    private static List<RankRow> rank(String subCategory, LocalDate asOf,
                                      Map<Pair, List<Scored>> buckets, int peerCount) {
        List<RankRow> rows = new ArrayList<>();
        for (Map.Entry<Pair, List<Scored>> e : buckets.entrySet()) {
            Pair p = e.getKey();
            List<Scored> scored = e.getValue();
            boolean higherIsBetter = p.metric().higherIsBetter();
            scored.sort((a, b) -> higherIsBetter
                    ? Double.compare(b.value(), a.value())
                    : Double.compare(a.value(), b.value()));

            int n = scored.size();
            for (int i = 0; i < n; i++) {
                int rankInCategory = i + 1;
                // 100 = best, 0 = worst; a single-fund group is defined as 100 rather than 0/0.
                double percentile = n == 1 ? 100.0 : (double) (n - rankInCategory) / (n - 1) * 100.0;
                percentile = Math.round(percentile * 100.0) / 100.0;
                rows.add(new RankRow(subCategory, asOf, p.horizon(), p.metric(),
                        scored.get(i).code(), rankInCategory, percentile, peerCount));
            }
        }
        return rows;
    }

    /** One fund's value on a metric, awaiting a rank. */
    private record Scored(int code, double value) {
    }
}
