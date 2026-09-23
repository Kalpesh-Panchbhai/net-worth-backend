package com.networth.networthbackend.stock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

/**
 * Read/update the per-market Slack alert schedule (the UI's "Alert Settings").
 *
 * <pre>
 *   GET /stock-alerts                                              — both markets' config
 *   PUT /stock-alerts  {market, enabled, hour, minute, timezone, weekdaysOnly}
 * </pre>
 */
@Slf4j
public class StockAlertApiHandler extends EntityHandlerBase {

    private final StockAlertConfigRepository repo = new StockAlertConfigRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        String method = request.getHttpMethod() == null ? "GET" : request.getHttpMethod().toUpperCase();
        return switch (method) {
            case "GET" -> ok(repo.findAllEnsuringDefaults());
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
        return ok(repo.findAllEnsuringDefaults());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
