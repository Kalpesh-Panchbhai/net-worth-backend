package com.networth.networthbackend.holdings;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class HoldingApiHandler extends EntityHandlerBase {

    private final HoldingRepository repository = new HoldingRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.DELETE -> delete(request);
            case Constants.Actions.GET -> listByAccountId(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.HOLDINGS, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        String name = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        String symbol = json.hasNonNull(Constants.Query.SYMBOL) ? json.get(Constants.Query.SYMBOL).asText() : null;
        if (accountId == null || accountId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_ACCOUNT_ID_REQUIRED);
        if (name == null || name.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_NAME_REQUIRED);
        if (symbol == null || symbol.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_SYMBOL_REQUIRED);
        log.debug(Constants.Logs.HOLDING_CREATE, accountId, name, symbol);
        Holding created = repository.create(accountId, name, symbol);
        log.info(Constants.Logs.HOLDING_CREATED, created.getId(), created.getAccountId(), created.getName(), created.getSymbol());
        return created(created);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.HOLDING_DELETE_AFFECTED, affected, id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.HOLDING_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private APIGatewayProxyResponseEvent listByAccountId(APIGatewayProxyRequestEvent request) throws Exception {
        Long accountId = parsePathId(request);
        if (accountId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.HOLDINGS_LIST, accountId);
        List<HoldingSummary> list = repository.listSummariesByAccountId(accountId);
        log.info(Constants.Logs.HOLDINGS_LIST_COUNT, list.size(), accountId);
        return ok(list);
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
