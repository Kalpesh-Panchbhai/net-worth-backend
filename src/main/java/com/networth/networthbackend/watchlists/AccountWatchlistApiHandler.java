package com.networth.networthbackend.watchlists;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountWatchlistApiHandler extends EntityHandlerBase {

    private final WatchlistSummaryService summaryService = new WatchlistSummaryService();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> listWatchlistsByAccountId(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.ACCOUNT_WATCHLISTS, action));
        };
    }

    private APIGatewayProxyResponseEvent listWatchlistsByAccountId(APIGatewayProxyRequestEvent request) throws Exception {
        Long accountId = parsePathId(request);
        if (accountId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.ACCOUNT_WATCHLISTS_LIST, accountId);
        var list = summaryService.summariesByAccountId(accountId);
        log.info(Constants.Logs.ACCOUNT_WATCHLISTS_LIST_COUNT, list.size(), accountId);
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
