package com.networth.networthbackend.transactions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionApiHandler extends EntityHandlerBase {

    private final TransactionRepository repository = new TransactionRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> list(request);
            case Constants.Actions.DELETE -> delete(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.TRANSACTIONS, action));
        };
    }

    private APIGatewayProxyResponseEvent list(APIGatewayProxyRequestEvent request) throws Exception {
        String accountIdStr = queryParam(request, Constants.Query.ACCOUNT_ID);
        String holdingIdStr = queryParam(request, Constants.Query.HOLDING_ID);
        if ((accountIdStr == null || accountIdStr.isBlank()) && (holdingIdStr == null || holdingIdStr.isBlank())) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_FILTER_REQUIRED);
        }
        if (holdingIdStr != null && !holdingIdStr.isBlank()) {
            long holdingId = Long.parseLong(holdingIdStr);
            log.debug(Constants.Logs.TRANSACTIONS_LIST, Constants.Query.HOLDING_ID, holdingId);
            var list = repository.listByHoldingId(holdingId);
            log.info(Constants.Logs.TRANSACTIONS_LIST_COUNT, list.size(), Constants.Query.HOLDING_ID, holdingId);
            return ok(list);
        }
        long accountId = Long.parseLong(accountIdStr);
        log.debug(Constants.Logs.TRANSACTIONS_LIST, Constants.Query.ACCOUNT_ID, accountId);
        var list = repository.listByAccountId(accountId);
        log.info(Constants.Logs.TRANSACTIONS_LIST_COUNT, list.size(), Constants.Query.ACCOUNT_ID, accountId);
        return ok(list);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        long id = Long.parseLong(idStr);
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.TRANSACTION_DELETE_AFFECTED, affected, id);
        if (affected == 0) return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.TRANSACTION_NOT_FOUND);
        return ApiGatewayResponses.noContent();
    }
}
