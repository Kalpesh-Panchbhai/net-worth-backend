package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.gmail.GmailSyncService;
import com.networth.networthbackend.refresh.RefreshSchedule;
import com.networth.networthbackend.tools.DailyDataRunner;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


/**
 * Scheduled entry point. The EventBridge rule fires this hourly; the actual heavy refresh only runs
 * when the user-configured schedule ({@link RefreshSchedule}) says it's due — so the interval,
 * weekend policy and enabled flag are all controllable from the UI without changing AWS.
 * <p>
 * A second, daily rule fires it with {@code {"job":"gmail-watch"}} to renew the Gmail push watch,
 * which Gmail expires after 7 days.
 */
@Slf4j
public class DailyDataHandler implements RequestHandler<Object, String> {
    @Override
    public String handleRequest(Object input, Context context) {
        if (input instanceof Map<?, ?> m && "gmail-watch".equals(m.get("job"))) {
            try {
                new GmailSyncService().renewWatch();
                return "gmail-watch-renewed";
            } catch (Exception e) {
                log.error("Gmail watch renewal failed: {}", e.getMessage(), e);
                return "error";
            }
        }
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
