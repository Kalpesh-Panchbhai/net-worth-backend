package com.networth.networthbackend.mf;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appends today's NAV to {@code mf_nav} from AMFI's NAVAll snapshot.
 * <p>
 * This is the steady-state path: one HTTP fetch for all of India, then ~1,760 upserts. mfapi.in is
 * only used for the initial history backfill, so the daily job stays independent of a third-party
 * API and costs one request regardless of how many funds are tracked.
 * <p>
 * Runs after {@link MfSchemeSyncService} so a newly launched fund is already in {@code mf_schemes}
 * and its first NAV lands the same day.
 */
@Slf4j
public class MfNavDailySyncService {

    private final AmfiClient amfi;
    private final MfNavRepository navRepository;
    private final MfSchemeRepository schemeRepository;

    public MfNavDailySyncService() {
        this(new AmfiClient(), new MfNavRepository(), new MfSchemeRepository());
    }

    public MfNavDailySyncService(AmfiClient amfi, MfNavRepository navRepository,
                                 MfSchemeRepository schemeRepository) {
        this.amfi = amfi;
        this.navRepository = navRepository;
        this.schemeRepository = schemeRepository;
    }

    public record Result(int tracked, int matched, int written) {
        public static Result empty() {
            return new Result(0, 0, 0);
        }
    }

    /**
     * Stores the latest NAV for every tracked scheme. Returns what was written.
     * <p>
     * Never throws: a missed NAV day must not fail the daily job. The upsert is keyed on
     * (scheme, date), so the next run corrects a revised NAV rather than duplicating it, and a
     * skipped day is filled by re-running.
     */
    public Result refresh() {
        try {
            return doRefresh();
        } catch (Exception e) {
            log.error("MF NAV daily sync failed: {}", e.getMessage(), e);
            return Result.empty();
        }
    }

    private Result doRefresh() throws Exception {
        List<Integer> tracked = schemeRepository.findRankableSchemeCodes();
        if (tracked.isEmpty()) {
            log.warn("No rankable schemes; run the scheme sync first");
            return Result.empty();
        }

        Map<Integer, AmfiClient.NavQuote> snapshot = amfi.fetchLiveNavs();
        if (snapshot.isEmpty()) {
            // An empty snapshot means the fetch or parse failed, not that trading stopped.
            log.warn("NAVAll snapshot empty; skipping write");
            return Result.empty();
        }

        // Only tracked schemes are stored. NAVAll carries ~14,400 rows, most of them IDCW and
        // Regular share classes that are deliberately out of scope.
        Set<Integer> trackedSet = new HashSet<>(tracked);
        List<MfNavRepository.Row> rows = new ArrayList<>(tracked.size());
        for (Map.Entry<Integer, AmfiClient.NavQuote> e : snapshot.entrySet()) {
            if (!trackedSet.contains(e.getKey())) continue;
            rows.add(new MfNavRepository.Row(e.getKey(), e.getValue().date(), e.getValue().nav()));
        }

        if (rows.isEmpty()) {
            log.warn("No tracked scheme appeared in the NAVAll snapshot; skipping write");
            return Result.empty();
        }

        int written = navRepository.upsertAll(rows);
        log.info("MF NAV daily sync: {} tracked, {} matched in snapshot, {} rows written",
                tracked.size(), rows.size(), written);
        return new Result(tracked.size(), rows.size(), written);
    }
}
