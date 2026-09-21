package com.networth.networthbackend.mf;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-time backfill of {@code mf_nav} from mfapi.in, one call per scheme.
 * <p>
 * Deliberately not part of the daily job. It makes ~1,760 requests to a free third-party API, so it
 * is rate-limited, resumable via {@code mf_schemes.nav_backfilled_at}, and meant to be run from
 * {@link MfNavBackfillRunner} on a workstation — a Lambda would hit its 15-minute ceiling.
 * <p>
 * After it completes, the daily AMFI sync is enough to keep the table current.
 */
@Slf4j
public class MfNavBackfillService {

    /**
     * Modest concurrency against someone else's free API. Four in flight with the delay below
     * averages roughly 6-8 requests/second, which puts a full run in the 5-10 minute range.
     */
    private static final int THREADS = 4;

    /** Per-request pause inside each worker, so bursts stay polite even when responses are fast. */
    private static final Duration REQUEST_DELAY = Duration.ofMillis(120);

    /** A scheme is retried this many times before being recorded as failed. */
    private static final int MAX_ATTEMPTS = 3;

    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(2);

    /** Progress is logged every this many schemes; a silent 10-minute run is indistinguishable from a hang. */
    private static final int LOG_EVERY = 100;

    /** How many completed schemes accumulate before their resume stamps are written. */
    private static final int STAMP_CHUNK = 50;

    private final MfApiClient mfApi;
    private final MfNavRepository navRepository;
    private final MfSchemeRepository schemeRepository;

    public MfNavBackfillService() {
        this(new MfApiClient(), new MfNavRepository(), new MfSchemeRepository());
    }

    public MfNavBackfillService(MfApiClient mfApi, MfNavRepository navRepository,
                                MfSchemeRepository schemeRepository) {
        this.mfApi = mfApi;
        this.navRepository = navRepository;
        this.schemeRepository = schemeRepository;
    }

    public record Result(int attempted, int succeeded, int empty, int failed, long rowsWritten,
                         List<Integer> failedSchemeCodes) {
    }

    /** Backfills every rankable scheme not yet stamped as done. */
    public Result run() throws Exception {
        List<Integer> pending = reconcile(schemeRepository.findSchemesNeedingBackfill());
        if (pending.isEmpty()) {
            log.info("NAV backfill: nothing pending");
            return new Result(0, 0, 0, 0, 0L, List.of());
        }
        log.info("NAV backfill: {} schemes pending, {} threads", pending.size(), THREADS);

        AtomicInteger done = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        AtomicInteger rowsWritten = new AtomicInteger();
        // Bounded so a pathological run cannot grow these without limit.
        var failed = new ArrayBlockingQueue<Integer>(pending.size());
        var backfilled = new ArrayBlockingQueue<Integer>(pending.size());

        Instant start = Instant.now();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (Integer schemeCode : pending) {
                pool.submit(() -> {
                    try {
                        int rows = fetchAndStore(schemeCode);
                        if (rows < 0) {
                            empty.incrementAndGet();
                            // Not stamped: an empty response may be a transient upstream problem,
                            // and mfapi.in answers an unknown code with 200 + empty data, so a 200
                            // is not evidence the history was really pulled.
                        } else {
                            rowsWritten.addAndGet(rows);
                            succeeded.incrementAndGet();
                            backfilled.offer(schemeCode);
                            // Stamped in chunks as work completes, not once at the end: a run that
                            // is killed part-way must not lose the progress it made, or a resume
                            // re-fetches every scheme it already stored.
                            if (backfilled.size() >= STAMP_CHUNK) flushStamps(backfilled);
                        }
                    } catch (Exception e) {
                        log.warn("NAV backfill failed for scheme {}: {}", schemeCode, e.getMessage());
                        failed.offer(schemeCode);
                    } finally {
                        int n = done.incrementAndGet();
                        if (n % LOG_EVERY == 0) {
                            log.info("NAV backfill: {}/{} schemes, {} rows, {}s elapsed",
                                    n, pending.size(), rowsWritten.get(),
                                    Duration.between(start, Instant.now()).toSeconds());
                        }
                    }
                });
            }
            pool.shutdown();
            // Generous ceiling: 1,760 schemes at the configured rate is minutes, not hours, but a
            // slow upstream should stall the run rather than silently truncate it.
            if (!pool.awaitTermination(2, TimeUnit.HOURS)) {
                log.error("NAV backfill timed out; shutting workers down");
                pool.shutdownNow();
            }
        } finally {
            if (!pool.isShutdown()) pool.shutdownNow();
        }

        flushStamps(backfilled);

        List<Integer> failedCodes = new ArrayList<>(failed);
        log.info("NAV backfill done in {}s: {} attempted, {} succeeded, {} empty, {} failed, {} rows written",
                Duration.between(start, Instant.now()).toSeconds(),
                pending.size(), succeeded.get(), empty.get(), failedCodes.size(), rowsWritten.get());
        if (!failedCodes.isEmpty()) {
            log.warn("Failed scheme codes (re-run to retry): {}", failedCodes);
        }
        return new Result(pending.size(), succeeded.get(), empty.get(), failedCodes.size(),
                rowsWritten.get(), failedCodes);
    }

    /**
     * Stamps and clears whatever has completed so far. Batched rather than one UPDATE per scheme:
     * 1,760 single-row round trips to a remote database cost more than the fetches do.
     */
    private void flushStamps(java.util.Queue<Integer> backfilled) {
        List<Integer> chunk = new ArrayList<>();
        for (Integer code = backfilled.poll(); code != null; code = backfilled.poll()) {
            chunk.add(code);
        }
        if (chunk.isEmpty()) return;
        try {
            schemeRepository.markBackfilled(chunk);
        } catch (Exception e) {
            // The NAV rows are already committed, so losing a stamp only means those schemes get
            // re-fetched on the next run — wasteful, not incorrect. Never fail the run for it.
            log.warn("Failed to stamp {} backfilled schemes: {}", chunk.size(), e.getMessage());
        }
    }

    /**
     * Stamps schemes that already hold NAV rows from an earlier interrupted run.
     * <p>
     * Recovers progress made before stamping was incremental, and more generally makes a resume
     * cheap after any hard kill: a scheme with history stored does not need fetching again.
     */
    private List<Integer> reconcile(List<Integer> pending) throws Exception {
        if (pending.isEmpty()) return pending;
        List<Integer> withoutHistory = navRepository.findSchemesWithoutHistory(pending);
        int alreadyStored = pending.size() - withoutHistory.size();
        if (alreadyStored > 0) {
            List<Integer> stored = new ArrayList<>(pending);
            stored.removeAll(withoutHistory);
            schemeRepository.markBackfilled(stored);
            log.info("NAV backfill: {} schemes already had history from an earlier run; stamped and skipped",
                    alreadyStored);
        }
        return withoutHistory;
    }

    /** Returns rows written, or -1 when the scheme has no history. Retries transient failures. */
    private int fetchAndStore(int schemeCode) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                List<AmfiClient.NavQuote> history = mfApi.fetchHistory(schemeCode);
                Thread.sleep(REQUEST_DELAY.toMillis());
                if (history.isEmpty()) return -1;

                List<MfNavRepository.Row> rows = new ArrayList<>(history.size());
                for (AmfiClient.NavQuote q : history) {
                    rows.add(new MfNavRepository.Row(schemeCode, q.date(), q.nav()));
                }
                return navRepository.upsertAll(rows);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_BACKOFF.toMillis() * attempt);
                }
            }
        }
        throw last;
    }
}
