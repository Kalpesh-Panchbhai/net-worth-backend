package com.networth.networthbackend.fx;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.tools.YahooFinanceClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fills {@code fx_rates_daily} from Yahoo Finance.
 * <p>
 * All supported pairs are derived from three USD legs (USD&rarr;INR, USD&rarr;EUR, USD&rarr;GBP),
 * so a full refresh costs three outbound calls rather than one per ordered pair:
 * {@code rate(A→B) = usdTo(B) / usdTo(A)}, with {@code usdTo(USD) = 1}.
 * <p>
 * This runs on the daily job (and manual {@code /refresh}) — never on a read request.
 */
@Slf4j
public class FxRateBackfillService {

    private static final String USD = "USD";
    /** Rates are stored with generous scale; a display value is rounded once, at the end. */
    private static final int RATE_SCALE = 10;
    /** Re-fetch a short tail every run so late/revised closes get corrected. */
    private static final int TAIL_REFETCH_DAYS = 10;
    /** Floor for the initial backfill when the DB has no history at all. */
    private static final int BOOTSTRAP_LOOKBACK_DAYS = 400;

    private final YahooFinanceClient yahoo;
    private final FxRateRepository repository;

    public FxRateBackfillService() {
        this(new YahooFinanceClient(), new FxRateRepository());
    }

    public FxRateBackfillService(YahooFinanceClient yahoo, FxRateRepository repository) {
        this.yahoo = yahoo;
        this.repository = repository;
    }

    /**
     * Brings the table up to {@code today}, fetching only what is missing.
     * Returns the number of rows written. Never throws: FX freshness must not fail the daily job.
     */
    public int refresh() {
        try {
            LocalDate today = LocalDate.now();
            int written = backfill(resolveStart(today), today);
            written += extendHistoryIfShort(today);
            return written;
        } catch (Exception e) {
            log.warn("FX backfill failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Fills in history the table is missing at the front.
     * <p>
     * {@link #resolveStart} only ever looks at {@code MAX(date)}, so once anything at all has been
     * written — in particular the recent window the request-path bootstrap writes — it would take
     * the tail-refetch branch forever and the older rates would never arrive. Every historical
     * chart point and transaction would then convert at the oldest rate that happened to be
     * stored. This closes that gap by comparing the stored floor against the earliest date the
     * data actually needs.
     */
    private int extendHistoryIfShort(LocalDate today) throws Exception {
        LocalDate storedFloor = repository.minDate();
        if (storedFloor == null) return 0; // nothing stored: resolveStart already covered it
        LocalDate needed = requiredFloor(today);
        if (!needed.isBefore(storedFloor)) return 0;
        log.info("FX history starts at {} but data goes back to {}; extending", storedFloor, needed);
        return backfill(needed, storedFloor);
    }

    /** The earliest date any stored amount needs a rate for. */
    private LocalDate requiredFloor(LocalDate today) throws Exception {
        LocalDate earliest = repository.earliestBusinessDate();
        LocalDate floor = today.minusDays(BOOTSTRAP_LOOKBACK_DAYS);
        if (earliest == null) return floor;
        return earliest.isBefore(floor) ? earliest : floor;
    }

    /** Fetches [start, end] for every supported pair and upserts it. */
    public int backfill(LocalDate start, LocalDate end) throws Exception {
        if (end.isBefore(start)) return 0;

        Map<String, TreeMap<LocalDate, BigDecimal>> usdLegs = fetchUsdLegs(start, end);
        // All legs or nothing. A partial fetch would still advance MAX(date), and the next run
        // would then only refetch a short tail — permanently truncating the missing currency's
        // history. Writing nothing keeps the watermark where it is so the next run retries.
        int expectedLegs = Constants.Currency.SUPPORTED.size() - 1; // USD is the identity leg
        if (usdLegs.size() < expectedLegs) {
            log.warn("FX backfill: fetched {} of {} USD legs for [{}..{}]; writing nothing so the next run retries",
                    usdLegs.size(), expectedLegs, start, end);
            return 0;
        }

        List<FxRateRepository.Row> rows = new ArrayList<>();
        for (String base : Constants.Currency.SUPPORTED) {
            for (String quote : Constants.Currency.SUPPORTED) {
                if (base.equals(quote)) continue; // identity is resolved in code, never stored
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    BigDecimal fromLeg = legRate(usdLegs, base, d);
                    BigDecimal toLeg = legRate(usdLegs, quote, d);
                    if (fromLeg == null || toLeg == null) continue;
                    // Both legs must be strictly POSITIVE, not merely non-zero. Guarding only the
                    // divisor would let a provider glitch reporting a 0 close store a rate of 0,
                    // and every amount converted through it would silently become 0.00 while
                    // still carrying the preferred-currency label.
                    if (fromLeg.signum() <= 0 || toLeg.signum() <= 0) {
                        log.warn("Skipping {}->{} on {}: non-positive leg ({} / {})", base, quote, d, toLeg, fromLeg);
                        continue;
                    }
                    rows.add(new FxRateRepository.Row(base, quote, d,
                            toLeg.divide(fromLeg, RATE_SCALE, RoundingMode.HALF_UP)));
                }
            }
        }
        log.info("FX backfill [{}..{}] -> {} rows across {} currencies", start, end, rows.size(), Constants.Currency.SUPPORTED.size());
        return repository.upsertAll(rows);
    }

    /** Continue from just behind the newest stored date, or from the start of the data if empty. */
    private LocalDate resolveStart(LocalDate today) throws Exception {
        LocalDate max = repository.maxDate();
        if (max != null) return max.minusDays(TAIL_REFETCH_DAYS);
        return requiredFloor(today);
    }

    /** USD -&gt; ccy series for every supported non-USD currency, forward-filled over the range. */
    private Map<String, TreeMap<LocalDate, BigDecimal>> fetchUsdLegs(LocalDate start, LocalDate end) {
        Map<String, TreeMap<LocalDate, BigDecimal>> legs = new HashMap<>();
        for (String ccy : Constants.Currency.SUPPORTED) {
            if (USD.equals(ccy)) continue;
            try {
                Map<LocalDate, BigDecimal> series = yahoo.getFxCalendarFilled(USD, ccy, start, end);
                if (series != null && !series.isEmpty()) legs.put(ccy, new TreeMap<>(series));
                else log.warn("FX backfill: empty series for USD->{}", ccy);
            } catch (Exception e) {
                log.warn("FX backfill: USD->{} fetch failed: {}", ccy, e.getMessage());
            }
        }
        return legs;
    }

    /** USD -&gt; {@code ccy} on {@code date}: exact, else nearest earlier, else nearest later. */
    private static BigDecimal legRate(Map<String, TreeMap<LocalDate, BigDecimal>> legs, String ccy, LocalDate date) {
        if (USD.equals(ccy)) return BigDecimal.ONE;
        TreeMap<LocalDate, BigDecimal> series = legs.get(ccy);
        if (series == null || series.isEmpty()) return null;
        BigDecimal exact = series.get(date);
        if (exact != null) return exact;
        Map.Entry<LocalDate, BigDecimal> floor = series.floorEntry(date);
        if (floor != null) return floor.getValue();
        Map.Entry<LocalDate, BigDecimal> ceil = series.ceilingEntry(date);
        return ceil == null ? null : ceil.getValue();
    }
}
