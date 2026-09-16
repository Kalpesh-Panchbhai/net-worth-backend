package com.networth.networthbackend.userconfig;

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
import java.util.regex.Pattern;

/**
 * {@code /user-config/{userId}?key=<name>} — a generic per-user settings store that syncs client
 * state across devices. GET returns {@code {"value": <json>}} ({@code null} when nothing is saved
 * for the key); PUT upserts the {@code value} from the request body. The value is opaque JSON, so
 * new client settings only need a new {@code key} — no backend change.
 */
@Slf4j
public class UserConfigApiHandler extends EntityHandlerBase {

    private static final String KEY = "key";
    private static final String VALUE = "value";
    /** Keeps keys to a safe, bounded shape (matches the VARCHAR(64) column). */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");

    private final UserConfigRepository repository = new UserConfigRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> get(request);
            case Constants.Actions.UPDATE -> put(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "user-config", action));
        };
    }

    private APIGatewayProxyResponseEvent get(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        String key = key(request);
        if (key == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'key' is required and must match [a-z0-9_-]{1,64}");
        }
        Optional<String> stored = repository.find(userId, key);
        ObjectNode out = MAPPER.createObjectNode();
        if (stored.isPresent()) out.set(VALUE, MAPPER.readTree(stored.get()));
        else out.putNull(VALUE);
        return ok(out);
    }

    private APIGatewayProxyResponseEvent put(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        String key = key(request);
        if (key == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'key' is required and must match [a-z0-9_-]{1,64}");
        }
        JsonNode body = getJsonBody(request);
        if (body == null || !body.has(VALUE)) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Request body must contain a 'value'");
        }
        repository.upsert(userId, key, MAPPER.writeValueAsString(body.get(VALUE)));
        log.info("Saved user config for userId={}, key={}", userId, key);
        return ApiGatewayResponses.noContent();
    }

    private String key(APIGatewayProxyRequestEvent request) {
        String key = queryParam(request, KEY);
        if (key == null) return null;
        key = key.trim();
        return KEY_PATTERN.matcher(key).matches() ? key : null;
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
