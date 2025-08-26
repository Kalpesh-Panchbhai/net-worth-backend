package com.networth.networthbackend.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserApiHandler extends EntityHandlerBase {

    private final UserRepository userRepository = new UserRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        switch (action.toLowerCase()) {
            case Constants.Actions.CREATE:
                return createOrEnsure(request);
            case Constants.Actions.GET:
                return getUser(request);
            default:
                return badRequest(
                        Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                        String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.USERS, action)
                );
        }
    }

    private APIGatewayProxyResponseEvent createOrEnsure(APIGatewayProxyRequestEvent request) throws Exception {
        String userId = extractUserId(request);
        if (userId == null || userId.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.USER_ID_REQUIRED);
        }
        log.debug(Constants.Logs.USER_ENSURE, userId);
        User user = userRepository.createIfNotExists(userId);
        log.info(Constants.Logs.USER_ENSURED, user.getId(), user.getUserId());
        return ok(user);
    }

    private APIGatewayProxyResponseEvent getUser(APIGatewayProxyRequestEvent request) throws Exception {
        String userId = extractUserId(request);
        if (userId == null || userId.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.USER_ID_REQUIRED);
        }
        return userRepository.findByUserId(userId)
                .map(this::ok)
                .orElseGet(() -> notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.USER_NOT_FOUND));
    }

    private String extractUserId(APIGatewayProxyRequestEvent request) throws Exception {
        String userId = null;
        JsonNode node = getJsonBody(request);
        if (node != null && node.hasNonNull(Constants.Query.USER_ID)) {
            userId = node.get(Constants.Query.USER_ID).asText();
        }
        if (userId == null || userId.isBlank()) {
            userId = queryParam(request, Constants.Query.USER_ID);
        }
        return userId;
    }
}
