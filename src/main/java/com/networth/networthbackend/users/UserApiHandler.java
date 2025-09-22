package com.networth.networthbackend.users;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.watchlists.WatchlistRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserApiHandler extends EntityHandlerBase {

    private final UserRepository userRepository = new UserRepository();
    private final WatchlistRepository watchlistRepository = new WatchlistRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> createOrEnsure(request);
            case Constants.Actions.DELETE -> deleteUser(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.USERS, action));
        };
    }

    private APIGatewayProxyResponseEvent createOrEnsure(APIGatewayProxyRequestEvent request) throws Exception {
        String externalUserId = extractExternalUserId(request);
        if (externalUserId == null || externalUserId.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.EXTERNAL_USER_ID_REQUIRED);
        }
        log.debug(Constants.Logs.USER_ENSURE, externalUserId);
        EnsureUserResult result = userRepository.ensure(externalUserId);
        User user = result.getUser();
        if (result.isCreated()) {
            watchlistRepository.create(user.getId(), "All");
        }
        log.info(Constants.Logs.USER_ENSURED, user.getId(), user.getExternalUserId());
        return result.isCreated() ? created(user) : ok(user);
    }

    private APIGatewayProxyResponseEvent deleteUser(APIGatewayProxyRequestEvent request) throws Exception {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = userRepository.deleteById(id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.USER_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private String extractExternalUserId(APIGatewayProxyRequestEvent request) throws Exception {
        String externalUserId = null;
        JsonNode node = getJsonBody(request);
        if (node != null && node.hasNonNull(Constants.Query.EXTERNAL_USER_ID)) {
            externalUserId = node.get(Constants.Query.EXTERNAL_USER_ID).asText();
        }
        return externalUserId;
    }
}
