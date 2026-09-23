package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.refresh.RefreshSchedule;
import com.networth.networthbackend.tools.DailyDataRunner;
import lombok.extern.slf4j.Slf4j;


/**
 * Scheduled entry point. The EventBridge rule fires this hourly; the actual heavy refresh only runs
 * when the user-configured schedule ({@link RefreshSchedule}) says it's due — so the interval,
 * weekend policy and enabled flag are all controllable from the UI without changing AWS.
 */
@Slf4j
public class DailyDataHandler implements RequestHandler<Object, String> {
    @Override
    public String handleRequest(Object input, Context context) {
        try {
            if (!new RefreshSchedule().isDue(System.currentTimeMillis())) {
                log.info("Refresh not due yet per configured schedule; skipping this tick");
                return "skipped";
            }
            DailyDataRunner.run();
            return "ran";
        } catch (Exception e) {
            log.error("Scheduled refresh failed: {}", e.getMessage(), e);
            return "error";
        }
    }
}
