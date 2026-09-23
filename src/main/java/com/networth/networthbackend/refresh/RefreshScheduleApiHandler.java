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
import java.util.List;
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
    private final EventBridgeScheduler eventBridge = new EventBridgeScheduler();

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
        int anchorHour = b != null && b.hasNonNull("anchorHour") ? b.get("anchorHour").asInt() : cur.anchorHour();
        int anchorMinute = b != null && b.hasNonNull("anchorMinute") ? b.get("anchorMinute").asInt() : cur.anchorMinute();
        String tz = b != null && b.hasNonNull("timezone") ? b.get("timezone").asText() : cur.timezone();
        boolean weekdaysOnly = b != null && b.has("weekdaysOnly") ? b.get("weekdaysOnly").asBoolean() : cur.weekdaysOnly();
        boolean enabled = b != null && b.has("enabled") ? b.get("enabled").asBoolean() : cur.enabled();
        schedule.save(interval, anchorHour, anchorMinute, tz, weekdaysOnly, enabled);

        // Push the new schedule to the actual EventBridge rule so AWS fires at these times.
        String warning = null;
        try {
            eventBridge.update(schedule.buildCron(schedule.get()), enabled);
        } catch (Exception e) {
            warning = "Saved, but updating the AWS EventBridge rule failed: " + e.getMessage();
            log.warn(warning, e);
        }
        Map<String, Object> body = body();
        if (warning != null) body.put("warning", warning);
        return ok(body);
    }

    private Map<String, Object> body() {
        RefreshSchedule.Config cfg = schedule.get();
        long now = System.currentTimeMillis();
        Map<String, Object> m = new HashMap<>();
        m.put("intervalHours", cfg.intervalHours());
        m.put("anchorHour", cfg.anchorHour());
        m.put("anchorMinute", cfg.anchorMinute());
        m.put("timezone", cfg.timezone());
        m.put("weekdaysOnly", cfg.weekdaysOnly());
        m.put("enabled", cfg.enabled());
        m.put("allowedIntervals", RefreshSchedule.ALLOWED_INTERVALS);
        try {
            String last = appMeta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
            m.put("lastRefreshedAt", last == null ? null : Long.parseLong(last));
        } catch (Exception e) {
            m.put("lastRefreshedAt", null);
        }
        List<Long> next = schedule.nextRuns(now, 10);
        m.put("nextRuns", next);
        m.put("nextRefreshAt", next.isEmpty() ? null : next.get(0));

        // Live view of the actual AWS EventBridge rule (what's really scheduled).
        try {
            EventBridgeScheduler.RuleInfo rule = eventBridge.describe();
            m.put("awsCron", rule.scheduleExpression());
            m.put("awsState", rule.state());
            m.put("awsRuleName", EventBridgeScheduler.RULE_NAME);
        } catch (Exception e) {
            log.warn("Could not read EventBridge rule: {}", e.getMessage());
            m.put("awsCron", null);
            m.put("awsState", null);
        }
        return m;
    }
}
