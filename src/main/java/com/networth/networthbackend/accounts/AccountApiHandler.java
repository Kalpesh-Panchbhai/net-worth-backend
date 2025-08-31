package com.networth.networthbackend.accounts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.watchlists.WatchlistAccountRepository;
import com.networth.networthbackend.watchlists.WatchlistRepository;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;

@Slf4j
public class AccountApiHandler extends EntityHandlerBase {

    private final AccountRepository repository = new AccountRepository();
    private final WatchlistRepository watchlistRepository = new WatchlistRepository();
    private final WatchlistAccountRepository watchlistAccountRepository = new WatchlistAccountRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.GET -> listByUserId(request);
            case Constants.Actions.DELETE -> delete(request);
            case Constants.Actions.UPDATE -> update(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.ACCOUNTS, action));
        };
    }

    private APIGatewayProxyResponseEvent listByUserId(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.ACCOUNTS_LIST, userId);
        var list = repository.listSummariesByUserId(userId);
        log.info(Constants.Logs.ACCOUNTS_LIST_COUNT, list.size(), userId);
        return ok(list);
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long userId = json.hasNonNull(Constants.Query.USER_ID) ? json.get(Constants.Query.USER_ID).asLong() : null;
        String name = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        String type = json.hasNonNull(Constants.Query.TYPE) ? json.get(Constants.Query.TYPE).asText() : null;
        String currency = json.hasNonNull(Constants.Query.CURRENCY) ? json.get(Constants.Query.CURRENCY).asText() : null;
        boolean isActive = !json.hasNonNull(Constants.Query.IS_ACTIVE) || json.get(Constants.Query.IS_ACTIVE).asBoolean();

        if (userId == null || userId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.ACCOUNT_USER_ID_REQUIRED);
        }
        if (name == null || name.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.ACCOUNT_NAME_REQUIRED);
        }
        if (type == null || type.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.ACCOUNT_TYPE_REQUIRED);
        }
        if (currency == null || currency.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.ACCOUNT_CURRENCY_REQUIRED);
        }
        log.debug(Constants.Logs.ACCOUNT_CREATE, userId, name, type, currency, isActive);
        Account created;
        try {
            created = repository.create(userId, name, type, isActive, currency);
        } catch (SQLIntegrityConstraintViolationException e) {
            log.warn(Constants.Logs.ACCOUNT_NAME_TYPE_DUPLICATE, userId, name, type);
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.ACCOUNT_NAME_TYPE_DUPLICATE);
        }
        log.info(Constants.Logs.ACCOUNT_CREATED, created.getId(), created.getUserId(), created.getName(), created.getType(), created.getCurrency(), created.isActive());

        var wlOpt = watchlistRepository.findByUserIdAndName(created.getUserId(), Constants.Messages.WATCHLIST_NAME_ALL);
        long watchlistId = wlOpt.get().getId();
        if (watchlistId > 0) {
            watchlistAccountRepository.link(watchlistId, created.getId());
        }
        return created(created);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.ACCOUNT_DELETE_AFFECTED, affected, id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.ACCOUNT_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        var existingOpt = repository.findById(id);
        if (existingOpt.isEmpty()) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.ACCOUNT_NOT_FOUND);
        }
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        String newName = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        Boolean newIsActive = json.hasNonNull(Constants.Query.IS_ACTIVE) ? json.get(Constants.Query.IS_ACTIVE).asBoolean() : null;
        if ((newName == null || newName.isBlank()) && newIsActive == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.ACCOUNT_UPDATE_NO_FIELDS);
        }
        var existing = existingOpt.get();
        log.debug(Constants.Logs.ACCOUNT_UPDATE, id, newName, newIsActive);
        int affected;
        try {
            affected = repository.updateNameAndIsActive(id, (newName != null && !newName.isBlank()) ? newName : null, newIsActive);
        } catch (SQLIntegrityConstraintViolationException e) {
            log.warn(Constants.Logs.ACCOUNT_NAME_TYPE_DUPLICATE, existing.getUserId(), (newName != null && !newName.isBlank()) ? newName : existing.getName(), existing.getType());
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.ACCOUNT_NAME_TYPE_DUPLICATE);
        }
        log.debug(Constants.Logs.ACCOUNT_UPDATE_AFFECTED, affected, id);
        var updated = repository.findById(id).orElse(null);
        if (updated == null) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.ACCOUNT_NOT_FOUND);
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
