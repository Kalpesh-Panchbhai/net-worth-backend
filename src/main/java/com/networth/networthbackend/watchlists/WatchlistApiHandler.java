package com.networth.networthbackend.watchlists;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchlistApiHandler extends EntityHandlerBase {

    private final WatchlistRepository repository = new WatchlistRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        switch (action.toLowerCase()) {
            case Constants.Actions.CREATE:
                return create(request);
            case Constants.Actions.DELETE:
                return delete(request);
            case Constants.Actions.UPDATE:
                return updateName(request);
            default:
                return badRequest(
                        Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                        String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.WATCHLISTS, action)
                );
        }
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long userId = json.hasNonNull(Constants.Query.USER_ID) ? json.get(Constants.Query.USER_ID).asLong() : null;
        String name = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        if (userId == null || userId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_USER_ID_REQUIRED);
        }
        if (name == null || name.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_NAME_REQUIRED);
        }
        if (repository.existsByUserIdAndName(userId, name)) {
            log.warn(Constants.Logs.WATCHLIST_NAME_DUPLICATE, userId, name);
            return ApiGatewayResponses.error(
                    Constants.Http.Status.CONFLICT,
                    Constants.ErrorCodes.CONFLICT,
                    Constants.Messages.WATCHLIST_NAME_DUPLICATE
            );
        }
        log.debug(Constants.Logs.WATCHLIST_CREATE, userId, name);
        Watchlist created = repository.create(userId, name);
        log.info(Constants.Logs.WATCHLIST_CREATED, created.getId(), created.getUserId(), created.getName());
        return created(created);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.WATCHLIST_DELETE_AFFECTED, affected, id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.WATCHLIST_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private APIGatewayProxyResponseEvent updateName(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        String newName = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        if (newName == null || newName.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_NAME_REQUIRED);
        }
        var existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.WATCHLIST_NOT_FOUND);
        }
        var existing = existingOpt.get();
        if (newName.equals(existing.getName())) {
            // No change, return current state
            return ok(existing);
        }
        if (repository.existsByUserIdAndName(existing.getUserId(), newName)) {
            log.warn(Constants.Logs.WATCHLIST_NAME_DUPLICATE, existing.getUserId(), newName);
            return ApiGatewayResponses.error(
                    Constants.Http.Status.CONFLICT,
                    Constants.ErrorCodes.CONFLICT,
                    Constants.Messages.WATCHLIST_NAME_DUPLICATE
            );
        }
        log.debug(Constants.Logs.WATCHLIST_UPDATE_NAME, id, newName);
        int affected = repository.updateName(id, newName);
        log.debug(Constants.Logs.WATCHLIST_UPDATE_NAME_AFFECTED, affected, id);
        var updated = repository.findById(id).orElse(null);
        if (updated == null) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.WATCHLIST_NOT_FOUND);
        }
        return ok(updated);
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
