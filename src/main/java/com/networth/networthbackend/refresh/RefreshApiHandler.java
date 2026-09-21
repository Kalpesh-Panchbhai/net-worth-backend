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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RefreshApiHandler extends EntityHandlerBase {

    private final AppMetaRepository appMeta = new AppMetaRepository();

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

    /** GET /refresh — epoch millis of the last successful refresh, or null if it has never run. */
    private APIGatewayProxyResponseEvent lastRefreshed() throws Exception {
        String stored = appMeta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
        Map<String, Object> body = new HashMap<>();
        body.put("lastRefreshedAt", stored == null ? null : Long.parseLong(stored));
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
            DailyDataRunner.run();
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
