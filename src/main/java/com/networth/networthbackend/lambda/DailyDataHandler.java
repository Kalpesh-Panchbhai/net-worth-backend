package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.tools.DailyDataRunner;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS Lambda handler that triggers the daily data backfill job.
 * <p>
 * Set environment variables in Lambda for DB connectivity:
 * - DB_URL, DB_USERNAME, DB_PASSWORD
 * Optional tuning:
 * - BATCH_SIZE (default 2000)
 * - MAX_ACCOUNTS (default 0 => no limit)
 */
public class DailyDataHandler implements RequestHandler<Map<String, Object>, String> {
    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            Map<String, String> params = new HashMap<>();
            String batchSize = System.getenv().getOrDefault("BATCH_SIZE", "2000");
            String maxAccounts = System.getenv().getOrDefault("MAX_ACCOUNTS", "0");
            params.put("batchSize", batchSize);
            params.put("maxAccounts", maxAccounts);

            DailyDataRunner.run(params);
            return "OK";
        } catch (Exception e) {
            // Return message for convenience; actual failure should be observed in logs/metrics
            return "ERROR: " + e.getMessage();
        }
    }
}
