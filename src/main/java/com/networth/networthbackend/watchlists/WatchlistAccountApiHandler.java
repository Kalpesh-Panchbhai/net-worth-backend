package com.networth.networthbackend.watchlists;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.AccountRepository;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WatchlistAccountApiHandler extends EntityHandlerBase {

    private final AccountRepository accountRepository = new AccountRepository();
    private final WatchlistAccountRepository repository = new WatchlistAccountRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> listAccountsByWatchlistId(request);
            case Constants.Actions.CREATE -> link(request);
            case Constants.Actions.DELETE -> unlink(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.WATCHLIST_ACCOUNTS, action));
        };
    }

    private APIGatewayProxyResponseEvent listAccountsByWatchlistId(APIGatewayProxyRequestEvent request) throws Exception {
        Long watchlistId = parsePathId(request);
        if (watchlistId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.WATCHLIST_ACCOUNTS_LIST, watchlistId);
        var list = accountRepository.listSummariesByWatchlistId(watchlistId);
        log.info(Constants.Logs.WATCHLIST_ACCOUNTS_LIST_COUNT, list.size(), watchlistId);
        return ok(list);
    }

    private APIGatewayProxyResponseEvent link(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long watchlistId = json.hasNonNull(Constants.Query.WATCHLIST_ID) ? json.get(Constants.Query.WATCHLIST_ID).asLong() : null;
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        if (watchlistId == null || watchlistId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_ID_REQUIRED);
        if (accountId == null || accountId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_ACCOUNT_ID_REQUIRED);
        log.debug(Constants.Logs.WATCHLIST_ACCOUNT_LINK, accountId, watchlistId);
        int affected = repository.link(watchlistId, accountId);
        log.debug(Constants.Logs.WATCHLIST_ACCOUNT_LINK_AFFECTED, affected, watchlistId, accountId);
        // idempotent; return 201 even if already exists
        return created(Constants.Api.EMPTY_JSON);
    }

    private APIGatewayProxyResponseEvent unlink(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long watchlistId = json.hasNonNull(Constants.Query.WATCHLIST_ID) ? json.get(Constants.Query.WATCHLIST_ID).asLong() : null;
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        if (watchlistId == null || watchlistId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_ID_REQUIRED);
        if (accountId == null || accountId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.WATCHLIST_ACCOUNT_ID_REQUIRED);
        int affected = repository.unlink(watchlistId, accountId);
        log.debug(Constants.Logs.WATCHLIST_ACCOUNT_UNLINK_AFFECTED, affected, watchlistId, accountId);
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
