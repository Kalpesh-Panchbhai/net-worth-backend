package com.networth.networthbackend.gmail;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Map;

/**
 * POST /gmail-webhook — the Pub/Sub push endpoint Gmail notifies on every new message with the watched label.
 * The push carries only {@code {emailAddress, historyId}}; the actual work is in
 * {@link GmailSyncService}. A non-2xx reply makes Pub/Sub redeliver, which is what we want for
 * transient failures; a bad or missing token gets 403 so forged calls are dropped.
 */
@Slf4j
public class GmailWebhookApiHandler extends EntityHandlerBase {

    private final PubSubTokenVerifier verifier = new PubSubTokenVerifier();
    private final GmailSyncService sync = new GmailSyncService();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (!Constants.Actions.CREATE.equalsIgnoreCase(action)) {
            return badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "gmail-webhook", action));
        }
        if (!verifier.verify(authorization(request))) {
            return ApiGatewayResponses.error(403, "FORBIDDEN", "Invalid push token");
        }
        JsonNode body = getJsonBody(request);
        String data = body == null ? null : body.path("message").path("data").asText(null);
        if (data == null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "message.data required");

        JsonNode notification = MAPPER.readTree(Base64.getDecoder().decode(data));
        String historyId = notification.path("historyId").asText(null);
        if (historyId == null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "historyId required");

        sync.onNotification(historyId);
        return ok(Map.of("status", "ok"));
    }

    private static String authorization(APIGatewayProxyRequestEvent request) {
        if (request.getHeaders() == null) return null;
        for (Map.Entry<String, String> h : request.getHeaders().entrySet()) {
            if ("authorization".equalsIgnoreCase(h.getKey())) return h.getValue();
        }
        return null;
    }
}
