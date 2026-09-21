package com.networth.networthbackend.mf;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fills {@code mf_schemes} from AMFI.
 * <p>
 * Scoped to live, open-ended, investable <b>Direct + Growth</b> share classes. IDCW share classes
 * drop NAV on every payout, so their CAGR is wrong without a total-return adjustment, and Regular
 * is the same portfolio at a higher expense ratio. That leaves roughly 1,760 funds out of AMFI's
 * ~16,500 rows — small enough to recompute every metric nightly in one pass.
 * <p>
 * The consequence to remember: Direct plans only exist from 2013-01-01, so no metric beyond about
 * 13.7 years is computable from this scope, and neither is a 2008-crash drawdown. Widening to
 * Regular later needs no migration — {@code plan} is already stored.
 * <p>
 * Runs on the daily job, never on a read request.
 */
@Slf4j
public class MfSchemeSyncService {

    /** AMFI's value in the "Scheme Type" column for open-ended schemes. */
    private static final String OPEN_ENDED = "OPEN ENDED";

    private final AmfiClient amfi;
    private final MfSchemeRepository repository;

    public MfSchemeSyncService() {
        this(new AmfiClient(), new MfSchemeRepository());
    }

    public MfSchemeSyncService(AmfiClient amfi, MfSchemeRepository repository) {
        this.amfi = amfi;
        this.repository = repository;
    }

    /** Counts from one sync, for logging and for the caller's sanity checks. */
    public record Result(
            int masterRows,
            int openEnded,
            int live,
            int investable,
            int directGrowth,
            int written,
            int markedNotLive,
            Map<String, Integer> unmappedCategories
    ) {
        public static Result empty() {
            return new Result(0, 0, 0, 0, 0, 0, 0, Map.of());
        }
    }

    /**
     * Refreshes the scheme master. Returns what was written.
     * <p>
     * Never throws: a stale scheme list must not fail the daily job, which also refreshes FX and
     * holdings. A failed fetch leaves the previous rows in place.
     */
    public Result refresh() {
        try {
            return doRefresh();
        } catch (Exception e) {
            log.error("MF scheme sync failed: {}", e.getMessage(), e);
            return Result.empty();
        }
    }

    private Result doRefresh() throws Exception {
        List<AmfiClient.SchemeRow> master = amfi.fetchSchemeMaster();
        Map<Integer, AmfiClient.NavQuote> liveNavs = amfi.fetchLiveNavs();

        if (master.isEmpty()) {
            log.warn("AMFI scheme master came back empty; skipping write");
            return Result.empty();
        }

        int openEnded = 0;
        int live = 0;
        int investable = 0;
        List<MfScheme> toWrite = new ArrayList<>();
        Map<String, Integer> unmapped = new TreeMap<>();
        Map<String, Integer> byAssetClass = new HashMap<>();

        for (AmfiClient.SchemeRow row : master) {
            if (!OPEN_ENDED.equalsIgnoreCase(row.schemeType().trim())) continue;
            openEnded++;

            AmfiClient.NavQuote quote = liveNavs.get(row.schemeCode());
            if (quote == null) continue;
            live++;

            if (MfPlanOption.isNotInvestable(row.navName())) continue;
            investable++;

            MfPlanOption.Plan plan = MfPlanOption.plan(row.navName());
            MfPlanOption.Option option = MfPlanOption.option(row.navName());
            if (plan != MfPlanOption.Plan.DIRECT || option != MfPlanOption.Option.GROWTH) continue;

            MfCategoryResolver.Category category = MfCategoryResolver.resolve(row.schemeCategory());
            if (!category.isRankable()) {
                // Still stored, so the fund is not lost — just excluded from rankings until the
                // resolver learns the label.
                unmapped.merge(row.schemeCategory(), 1, Integer::sum);
            }
            byAssetClass.merge(category.assetClass().name(), 1, Integer::sum);

            toWrite.add(MfScheme.builder()
                    .schemeCode(row.schemeCode())
                    .isin(row.isin())
                    .amc(row.amc())
                    .name(row.schemeName())
                    .navName(row.navName())
                    .rawCategory(row.schemeCategory())
                    .assetClass(category.assetClass())
                    .subCategory(category.subCategory())
                    .plan(plan)
                    .option(option)
                    .launchDate(row.launchDate())
                    .isLive(true)
                    .lastNavDate(quote.date())
                    .build());
        }

        if (toWrite.isEmpty()) {
            // The parse succeeded but matched nothing, which means AMFI changed the file's shape
            // rather than that every fund disappeared. Leave the existing rows alone.
            log.warn("No Direct+Growth schemes matched out of {} master rows; skipping write", master.size());
            return Result.empty();
        }

        int written = repository.upsertAll(toWrite);
        int markedNotLive = repository.markMissingAsNotLive(
                toWrite.stream().map(MfScheme::schemeCode).toList());

        if (!unmapped.isEmpty()) repository.recordUnmappedCategories(unmapped);

        log.info("MF scheme sync: {} master rows -> {} open-ended -> {} live -> {} investable -> {} Direct+Growth; "
                        + "{} written, {} marked not live, {} unmapped categories, {} spellings known",
                master.size(), openEnded, live, investable, toWrite.size(),
                written, markedNotLive, unmapped.size(), MfCategoryResolver.knownSpellings());
        log.info("MF schemes by asset class: {}", new TreeMap<>(byAssetClass));

        return new Result(master.size(), openEnded, live, investable, toWrite.size(),
                written, markedNotLive, unmapped);
    }

    /** Prints the resolved category breakdown. Used by {@link MfSchemeSyncRunner} to eyeball a run. */
    public void logCategoryBreakdown() throws java.sql.SQLException {
        Map<String, Integer> counts = repository.countByCategory();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Live Direct+Growth schemes by category ({} total):", total);
        counts.forEach((category, count) ->
                log.info("  {} {}", String.format(Locale.ROOT, "%5d", count), category));
    }
}
