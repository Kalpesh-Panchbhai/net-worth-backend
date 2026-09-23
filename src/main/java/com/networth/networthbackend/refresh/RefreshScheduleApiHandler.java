package com.networth.networthbackend.refresh;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.meta.AppMetaRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Read/update the data-refresh schedule (interval, weekend policy, enabled).
 *
 * <pre>
 *   GET /refresh-schedule                                    — current config + last/next run
 *   PUT /refresh-schedule  {intervalHours, weekdaysOnly, enabled}
 * </pre>
 */
@Slf4j
public class RefreshScheduleApiHandler extends EntityHandlerBase {

    private final RefreshSchedule schedule = new RefreshSchedule();
    private final AppMetaRepository appMeta = new AppMetaRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        String method = request.getHttpMethod() == null ? "GET" : request.getHttpMethod().toUpperCase();
        return switch (method) {
            case "GET" -> ok(body());
            case "PUT", "POST", "PATCH" -> update(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, "Unsupported method: " + method);
        };
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode b = getJsonBody(request);
        RefreshSchedule.Config cur = schedule.get();
        int interval = b != null && b.hasNonNull("intervalHours") ? b.get("intervalHours").asInt() : cur.intervalHours();
        boolean weekdaysOnly = b != null && b.has("weekdaysOnly") ? b.get("weekdaysOnly").asBoolean() : cur.weekdaysOnly();
        boolean enabled = b != null && b.has("enabled") ? b.get("enabled").asBoolean() : cur.enabled();
        schedule.save(interval, weekdaysOnly, enabled);
        return ok(body());
    }

    private Map<String, Object> body() {
        RefreshSchedule.Config cfg = schedule.get();
        long now = System.currentTimeMillis();
        Map<String, Object> m = new HashMap<>();
        m.put("intervalHours", cfg.intervalHours());
        m.put("weekdaysOnly", cfg.weekdaysOnly());
        m.put("enabled", cfg.enabled());
        m.put("allowedIntervals", RefreshSchedule.ALLOWED_INTERVALS);
        try {
            String last = appMeta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
            m.put("lastRefreshedAt", last == null ? null : Long.parseLong(last));
        } catch (Exception e) {
            m.put("lastRefreshedAt", null);
        }
        m.put("nextRefreshAt", schedule.nextRunAt(now));
        return m;
    }
}
