package com.networth.networthbackend.insights;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * {@code /insights-config/{userId}} — GET returns the saved dashboard layout ({@code {"layout": [...] }},
 * with {@code null} when the user has none yet); PUT upserts it. The layout is stored and echoed as
 * an opaque JSON array, so new widget types need no backend change.
 */
@Slf4j
public class InsightsConfigApiHandler extends EntityHandlerBase {

    private static final String LAYOUT = "layout";
    private final InsightsLayoutRepository repository = new InsightsLayoutRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> get(request);
            case Constants.Actions.UPDATE -> put(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "insights-config", action));
        };
    }

    private APIGatewayProxyResponseEvent get(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        Optional<String> stored = repository.findByUserId(userId);
        ObjectNode out = MAPPER.createObjectNode();
        if (stored.isPresent()) out.set(LAYOUT, MAPPER.readTree(stored.get()));
        else out.putNull(LAYOUT);
        return ok(out);
    }

    private APIGatewayProxyResponseEvent put(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        JsonNode body = getJsonBody(request);
        if (body == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        JsonNode layout = body.get(LAYOUT);
        if (layout == null || !layout.isArray()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "'layout' must be a JSON array");
        }
        repository.upsert(userId, MAPPER.writeValueAsString(layout));
        log.info("Saved insights layout for userId={} ({} widgets)", userId, layout.size());
        return ApiGatewayResponses.noContent();
    }

    private Long parsePathId(APIGatewayProxyRequestEvent request) {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) return null;
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
