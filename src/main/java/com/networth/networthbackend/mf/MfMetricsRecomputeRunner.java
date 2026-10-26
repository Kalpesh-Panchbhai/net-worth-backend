package com.networth.networthbackend.mf;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the mutual-fund metrics/ranks precompute once, from the command line.
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes:$(cat cp.txt) com.networth.networthbackend.mf.MfMetricsRecomputeRunner
 * </pre>
 * Prefer this for the first population after deploying {@code scripts/migrations/add-mf-metrics.sql}: the pass
 * fetches every fund's NAV history (~2 minutes) and so will not fit API Gateway's 29-second timeout
 * if triggered through {@code POST /refresh}. It runs nightly on its own via {@code DailyDataRunner}.
 * Idempotent — re-run it freely.
 */
@Slf4j
public class MfMetricsRecomputeRunner {

    public static void main(String[] args) throws Exception {
        try {
            MfMetricsRecomputeService.Result result = new MfMetricsRecomputeService().recompute();
            log.info("Recompute result: as_of={}, {} categories, {} funds, {} metric rows, {} rank rows",
                    result.asOf(), result.categories(), result.funds(), result.metricRows(), result.rankRows());
        } finally {
            Database.shutdown();
        }
    }
}
