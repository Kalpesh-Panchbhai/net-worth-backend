package com.networth.networthbackend.mf;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the NAV backfill and then reports coverage.
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes:$(cat cp.txt) com.networth.networthbackend.mf.MfNavBackfillRunner
 * </pre>
 * Resumable — re-run it to retry whatever failed. The coverage report at the end is the point:
 * nothing downstream should be built until the NAV table is known to be clean, since every metric
 * inherits a gap or a bad NAV silently.
 */
@Slf4j
public class MfNavBackfillRunner {

    /**
     * When SEBI mandated Direct plans, and so where most Direct NAV history starts.
     * <p>
     * Not a hard floor: a handful of AMCs sold direct-to-investor beforehand, so their Direct plan
     * legitimately has older history — Quantum's goes back to 2006. Schemes below this date are
     * reported for inspection, not treated as corrupt.
     */
    private static final LocalDate DIRECT_PLAN_MANDATE = LocalDate.of(2013, 1, 1);

    /** Below this many points a fund cannot support even a 1-year metric, so it is worth flagging. */
    private static final int SPARSE_POINT_THRESHOLD = 200;

    public static void main(String[] args) throws Exception {
        try {
            MfNavBackfillService.Result result = new MfNavBackfillService().run();
            log.info("Backfill result: {} attempted, {} succeeded, {} empty, {} failed, {} rows",
                    result.attempted(), result.succeeded(), result.empty(), result.failed(),
                    result.rowsWritten());

            report();
        } finally {
            Database.shutdown();
        }
    }

    /** Validates what actually landed: row totals, date span, sparse series, impossible dates. */
    private static void report() throws Exception {
        MfNavRepository navRepository = new MfNavRepository();
        Map<Integer, MfNavRepository.Coverage> coverage = navRepository.coverage();

        long totalRows = navRepository.totalRows();
        log.info("=== mf_nav coverage: {} schemes, {} rows total", coverage.size(), totalRows);
        if (coverage.isEmpty()) return;

        LocalDate earliest = null;
        LocalDate latest = null;
        int sparse = 0;
        int beforeDirectMandate = 0;
        Map<Integer, Integer> pointsHistogram = new TreeMap<>();

        for (MfNavRepository.Coverage c : coverage.values()) {
            if (earliest == null || c.from().isBefore(earliest)) earliest = c.from();
            if (latest == null || c.to().isAfter(latest)) latest = c.to();
            if (c.points() < SPARSE_POINT_THRESHOLD) sparse++;
            // Worth eyeballing rather than failing on: a pre-2013 Direct series is real for the few
            // AMCs that sold direct before the mandate, but would also be the signature of a series
            // that is actually the Regular plan's.
            if (c.from().isBefore(DIRECT_PLAN_MANDATE)) beforeDirectMandate++;
            pointsHistogram.merge(c.points() / 500 * 500, 1, Integer::sum);
        }

        log.info("date span: {} .. {}", earliest, latest);
        log.info("schemes with < {} NAV points: {}", SPARSE_POINT_THRESHOLD, sparse);
        log.info("schemes with history before the {} Direct mandate (inspect, may be legitimate): {}",
                DIRECT_PLAN_MANDATE, beforeDirectMandate);
        log.info("points per scheme (bucketed by 500): {}", pointsHistogram);
        log.info("average points per scheme: {}", totalRows / coverage.size());
    }
}
