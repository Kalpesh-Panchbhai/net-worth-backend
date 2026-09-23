package com.networth.networthbackend.stock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.refresh.EventBridgeScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read/update the per-market Slack alert schedule (the UI's "Alert Settings"). Each market maps to
 * its own EventBridge rule that fires {@code NetWorthStockAlertFunction} at the configured time; the
 * UI reads the live rule (cron/state) and updates it here.
 *
 * <pre>
 *   GET /stock-alerts                                              — both markets' config + live AWS rule
 *   PUT /stock-alerts  {market, enabled, hour, minute, timezone, weekdaysOnly}
 * </pre>
 */
@Slf4j
public class StockAlertApiHandler extends EntityHandlerBase {

    private final StockAlertConfigRepository repo = new StockAlertConfigRepository();
    private final EventBridgeScheduler eventBridge = new EventBridgeScheduler();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        String method = request.getHttpMethod() == null ? "GET" : request.getHttpMethod().toUpperCase();
        return switch (method) {
            case "GET" -> ok(withAwsRules());
            case "PUT", "POST", "PATCH" -> update(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, "Unsupported method: " + method);
        };
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode b = getJsonBody(request);
        String market = b != null && b.hasNonNull("market") && "us".equalsIgnoreCase(b.get("market").asText()) ? "us" : "in";
        boolean enabled = b == null || !b.has("enabled") || b.get("enabled").asBoolean();
        int hour = clamp(b != null && b.hasNonNull("hour") ? b.get("hour").asInt() : 17, 0, 23);
        int minute = clamp(b != null && b.hasNonNull("minute") ? b.get("minute").asInt() : 0, 0, 59);
        String tz = b != null && b.hasNonNull("timezone") ? b.get("timezone").asText() : "Asia/Kolkata";
        boolean weekdaysOnly = b == null || !b.has("weekdaysOnly") || b.get("weekdaysOnly").asBoolean();
        repo.upsert(new StockAlertConfig(market, enabled, hour, minute, tz, weekdaysOnly, null));

        // Push this market's schedule to its EventBridge rule.
        String warning = null;
        try {
            eventBridge.update(EventBridgeScheduler.alertRule(market),
                    EventBridgeScheduler.dailyCron(hour, minute, tz, weekdaysOnly), enabled);
        } catch (Exception e) {
            warning = "Saved, but updating the AWS EventBridge rule failed: " + e.getMessage();
            log.warn(warning, e);
        }
        List<Map<String, Object>> rows = withAwsRules();
        if (warning != null && !rows.isEmpty()) {
            for (Map<String, Object> r : rows) if (market.equals(r.get("market"))) r.put("warning", warning);
        }
        return ok(rows);
    }

    /** Config for both markets, each enriched with the live EventBridge rule cron + state. */
    private List<Map<String, Object>> withAwsRules() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockAlertConfig cfg : repo.findAllEnsuringDefaults()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("market", cfg.market());
            m.put("enabled", cfg.enabled());
            m.put("hour", cfg.hour());
            m.put("minute", cfg.minute());
            m.put("timezone", cfg.timezone());
            m.put("weekdaysOnly", cfg.weekdaysOnly());
            m.put("lastSentDate", cfg.lastSentDate() == null ? null : cfg.lastSentDate().toString());
            try {
                EventBridgeScheduler.RuleInfo rule = eventBridge.describe(EventBridgeScheduler.alertRule(cfg.market()));
                m.put("awsCron", rule.scheduleExpression());
                m.put("awsState", rule.state());
            } catch (Exception e) {
                log.warn("Could not read alert rule for {}: {}", cfg.market(), e.getMessage());
                m.put("awsCron", null);
                m.put("awsState", null);
            }
            out.add(m);
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
