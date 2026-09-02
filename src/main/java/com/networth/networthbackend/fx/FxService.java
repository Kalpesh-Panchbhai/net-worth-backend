package com.networth.networthbackend.fx;

import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serves display-currency FX rates to the request path.
 * <p>
 * Rates come from {@code fx_rates_daily} (written by the daily job), never from an outbound HTTP
 * call, and are memoised per JVM so a warm container answers most requests without touching the
 * database either. There are only four supported currencies, so the cache holds at most twelve
 * pairs and a few thousand dates.
 * <p>
 * If the table has no rows for a pair — a fresh deployment before the first daily run — a
 * rate-limited emergency fill fetches and persists them, so behaviour degrades in freshness
 * rather than correctness until the daily job takes over.
 */
@Slf4j
public final class FxService {

    /** How long a cached series may be reused before the tail is re-read for today's close. */
    private static final long TTL_MS = 10 * 60 * 1000L;
    /** Widen every DB read so nearby requests reuse one cache entry instead of thrashing it. */
    private static final int PREFETCH_PAD_DAYS = 45;

    /** Minimum gap between request-path bootstrap attempts; the daily job is the real filler. */
    private static final long BOOTSTRAP_COOLDOWN_MS = 10 * 60 * 1000L;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_BOOTSTRAP_MILLIS = new AtomicLong(0L);

    private static final FxRateRepository REPOSITORY = new FxRateRepository();

    private FxService() {
    }

    public static String normalize(String currency) {
        if (currency == null || currency.isBlank()) return Constants.Currency.DEFAULT;
        // Locale.ROOT: under a Turkish default locale "i" upper-cases to a dotted capital, so a
        // lowercase "inr" would fail the SUPPORTED check and be silently replaced by the default.
        String upper = currency.trim().toUpperCase(java.util.Locale.ROOT);
        return Constants.Currency.SUPPORTED.contains(upper) ? upper : Constants.Currency.DEFAULT;
    }

    /** Rates converting {@code source} into {@code target} across [start, end]. Never null. */
    public static FxRates rates(String source, String target, LocalDate start, LocalDate end) {
        return rates(List.of(normalize(source)), target, start, end)
                .getOrDefault(normalize(source), FxRates.unavailable());
    }

    /**
     * Rates converting each of {@code sources} into {@code target}, resolved in a single database
     * read. Every requested source is present in the result (identity when it equals the target).
     */
    public static Map<String, FxRates> rates(Collection<String> sources, String target, LocalDate start, LocalDate end) {
        String quote = normalize(target);
        LocalDate from = start == null ? LocalDate.now() : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        if (to.isBefore(from)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }

        Map<String, FxRates> out = new HashMap<>();
        Set<String> needed = new HashSet<>();
        // Pad the window so an adjacent request (a different time period on the same page) hits cache.
        LocalDate loadFrom = from.minusDays(PREFETCH_PAD_DAYS);
        LocalDate loadTo = max(to, LocalDate.now());

        for (String source : sources) {
            String base = normalize(source);
            if (base.equals(quote)) {
                out.put(base, FxRates.identity());
                continue;
            }
            Entry cached = CACHE.get(key(base, quote));
            if (cached != null && cached.covers(from, to)) {
                out.put(base, cached.rates);
                continue;
            }
            needed.add(base);
            // Reload at least as wide as whatever the stale entry held, so an expiring "ALL" window
            // is not replaced by a narrow one that the next chart request has to widen again.
            if (cached != null) {
                if (cached.from().isBefore(loadFrom)) loadFrom = cached.from();
                if (cached.to().isAfter(loadTo)) loadTo = cached.to();
            }
        }
        if (needed.isEmpty()) return out;

        final LocalDate windowStart = loadFrom;
        final LocalDate windowEnd = loadTo;
        Map<String, TreeMap<LocalDate, BigDecimal>> loaded = load(needed, quote, windowStart, windowEnd);

        // One backfill fills every pair, so bootstrap once for the whole batch rather than per base.
        boolean anyMissing = false;
        for (String base : needed) {
            if (isEmpty(loaded.get(base))) {
                anyMissing = true;
                break;
            }
        }
        if (anyMissing && bootstrap(windowStart, windowEnd)) {
            loaded = load(needed, quote, windowStart, windowEnd);
        }

        for (String base : needed) {
            FxRates fx = FxRates.of(loaded.get(base));
            // The entry records the window we ASKED the database for, which is what tells us not
            // to query it again — keying on the series' actual span would re-query forever
            // whenever the table genuinely has no rate that far back. Whether a rate exists for a
            // given date is a separate question, and callers converting historical amounts ask it
            // via FxRates.covers(date).
            // An unresolvable pair is not cached, so the next request retries once the daily job
            // has filled the table rather than serving native amounts for the whole TTL.
            if (fx.isResolvable()) CACHE.put(key(base, quote), new Entry(fx, windowStart, windowEnd));
            out.put(base, fx);
        }
        return out;
    }

    private static boolean isEmpty(TreeMap<LocalDate, BigDecimal> series) {
        return series == null || series.isEmpty();
    }

    /**
     * Drops this JVM's memo. The daily job calls it so its own process sees what it just wrote;
     * other API containers pick the new rates up when their entries pass {@link #TTL_MS}.
     */
    public static void invalidate() {
        CACHE.clear();
    }

    private static Map<String, TreeMap<LocalDate, BigDecimal>> load(Set<String> bases, String quote, LocalDate from, LocalDate to) {
        try {
            return REPOSITORY.loadSeries(new ArrayList<>(bases), quote, from, to);
        } catch (Exception e) {
            log.warn("FX rate load failed for {}->{} [{}..{}]: {}", bases, quote, from, to, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Emergency fill for a table the daily job has not populated yet, so the very first requests
     * after a deployment still show converted values.
     * <p>
     * This is the slow path — provider calls plus a multi-row write, on a read request — so it is
     * rate-limited globally rather than per window. Keying it per window would let one page load
     * fire several bootstraps at once, because its endpoints ask for different ranges (a summary
     * wants [yesterday, today], an ALL-period chart wants years). One caller wins the attempt;
     * everyone else gets {@code false} and serves native amounts until the rates land.
     */
    private static boolean bootstrap(LocalDate from, LocalDate to) {
        long now = System.currentTimeMillis();
        long previous = LAST_BOOTSTRAP_MILLIS.get();
        if (now - previous < BOOTSTRAP_COOLDOWN_MS) return false;
        if (!LAST_BOOTSTRAP_MILLIS.compareAndSet(previous, now)) return false; // another thread won
        log.info("No stored FX for [{}..{}]; bootstrapping from provider", from, to);
        try {
            return new FxRateBackfillService().backfill(from, to) > 0;
        } catch (Exception e) {
            log.warn("FX bootstrap failed for [{}..{}]: {}", from, to, e.getMessage());
            return false;
        }
    }

    private static String key(String base, String quote) {
        return base + '>' + quote;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private record Entry(FxRates rates, LocalDate from, LocalDate to, long loadedAtMillis) {
        Entry(FxRates rates, LocalDate from, LocalDate to) {
            this(rates, from, to, System.currentTimeMillis());
        }

        boolean covers(LocalDate start, LocalDate end) {
            if (System.currentTimeMillis() - loadedAtMillis > TTL_MS) return false;
            return !start.isBefore(from) && !end.isAfter(to);
        }
    }
}
