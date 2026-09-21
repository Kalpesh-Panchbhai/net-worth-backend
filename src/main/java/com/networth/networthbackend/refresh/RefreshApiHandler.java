package com.networth.networthbackend.refresh;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.meta.AppMetaRepository;
import com.networth.networthbackend.tools.DailyDataRunner;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RefreshApiHandler extends EntityHandlerBase {

    private final AppMetaRepository appMeta = new AppMetaRepository();

    /**
     * The UTC hours the scheduled refresh runs at, mirroring the EventBridge rule
     * {@code cron(30 0/8 ? * * *)} (every 8 hours at :30). Keep in sync with that rule.
     */
    private static final int[] REFRESH_HOURS_UTC = {0, 8, 16};
    private static final int REFRESH_MINUTE_UTC = 30;

    /** Epoch millis of the next scheduled refresh, derived from {@link #REFRESH_HOURS_UTC}. */
    private static long nextScheduledRefresh() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
            for (int hour : REFRESH_HOURS_UTC) {
                ZonedDateTime candidate = now.toLocalDate().plusDays(dayOffset)
                        .atTime(hour, REFRESH_MINUTE_UTC).atZone(ZoneOffset.UTC);
                if (candidate.isAfter(now)) return candidate.toInstant().toEpochMilli();
            }
        }
        return now.plusHours(8).toInstant().toEpochMilli(); // unreachable, but a safe fallback
    }

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }

        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> refreshData();
            case Constants.Actions.GET -> lastRefreshed();
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "refresh", action));
        };
    }

    /** GET /refresh — when data was last refreshed and when the next scheduled refresh is due. */
    private APIGatewayProxyResponseEvent lastRefreshed() throws Exception {
        String stored = appMeta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
        Map<String, Object> body = new HashMap<>();
        body.put("lastRefreshedAt", stored == null ? null : Long.parseLong(stored));
        body.put("nextRefreshAt", nextScheduledRefresh());
        return ok(body);
    }

    /**
     * Only one refresh at a time. The job rewrites daily rows and holds a write transaction for its
     * whole duration, so two concurrent runs would contend on the same rows and burn two pooled
     * connections. The local HTTP server used to serialise this accidentally by being
     * single-threaded; now that it has a real worker pool, the guard has to be explicit.
     */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private APIGatewayProxyResponseEvent refreshData() {
        if (!RUNNING.compareAndSet(false, true)) {
            log.info("Refresh already in progress; rejecting the concurrent request");
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT,
                    Constants.ErrorCodes.CONFLICT, Constants.Messages.REFRESH_ALREADY_RUNNING);
        }
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting manual data refresh");
            // Skip the heavy MF-analyser maintenance (AMFI sync + ~2-min recompute): it would blow
            // API Gateway's 29s timeout and does not affect the user's displayed portfolio values.
            // The 8-hourly scheduled run keeps the analyser catalog fresh.
            DailyDataRunner.run(false);
            long durationMs = System.currentTimeMillis() - startTime;

            log.info("Manual data refresh completed successfully in {} ms", durationMs);

            return ok(Map.of(
                    "message", "Data refreshed successfully",
                    "durationMs", durationMs,
                    "lastRefreshedAt", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Manual data refresh failed after {} ms: {}", durationMs, e.getMessage(), e);

            return badRequest(Constants.ErrorCodes.REFRESH_FAILED, "Data refresh failed: " + e.getMessage());
        } finally {
            RUNNING.set(false);
        }
    }
}
