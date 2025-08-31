package com.networth.networthbackend.transactions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLIntegrityConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
public class TransactionApiHandler extends EntityHandlerBase {

    private final TransactionRepository repository = new TransactionRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.GET -> list(request);
            case Constants.Actions.DELETE -> delete(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.TRANSACTIONS, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        Long holdingId = json.hasNonNull(Constants.Query.HOLDING_ID) ? json.get(Constants.Query.HOLDING_ID).asLong() : null;
        String txnDateStr = json.hasNonNull(Constants.Query.TXN_DATE) ? json.get(Constants.Query.TXN_DATE).asText() : null;
        BigDecimal invested = json.hasNonNull(Constants.Query.INVESTED) ? json.get(Constants.Query.INVESTED).decimalValue() : null;
        BigDecimal value = json.hasNonNull(Constants.Query.VALUE) ? json.get(Constants.Query.VALUE).decimalValue() : null;
        BigDecimal valueDelta = json.hasNonNull("valueDelta") ? json.get("valueDelta").decimalValue() : null;
        BigDecimal investedDelta = json.hasNonNull("investedDelta") ? json.get("investedDelta").decimalValue() : null;

        if (accountId == null || accountId <= 0)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_ACCOUNT_ID_REQUIRED);
        if (holdingId == null || holdingId < 0)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_HOLDING_ID_REQUIRED);
        if (txnDateStr == null || txnDateStr.isBlank())
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_DATE_REQUIRED);
        boolean hasAbs = (value != null) && (invested != null);
        boolean hasDelta = (valueDelta != null) && (investedDelta != null);
        if (hasAbs == hasDelta) {
            // Either neither provided, or both kinds mixed/provided
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_ABSOLUTE_OR_DELTAS_REQUIRED);
        }

        LocalDate txnDate;
        try {
            txnDate = LocalDate.parse(txnDateStr);
        } catch (Exception e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_DATE_REQUIRED);
        }
        // If using deltas, compute both from latest
        if (hasDelta) {
            java.util.Optional<Transaction> latestOpt;
            if (holdingId != null && holdingId > 0) {
                latestOpt = repository.findLatestByHoldingId(holdingId);
            } else {
                latestOpt = repository.findLatestByAccountId(accountId);
            }
            BigDecimal prevVal = latestOpt.map(Transaction::getValue).orElse(BigDecimal.ZERO);
            BigDecimal prevInv = latestOpt.map(Transaction::getInvested).orElse(BigDecimal.ZERO);
            value = prevVal.add(valueDelta);
            invested = prevInv.add(investedDelta);
        }
        log.debug(Constants.Logs.TRANSACTION_CREATE, accountId, holdingId, txnDate, invested, value);
        Transaction created;
        try {
            created = repository.create(accountId, holdingId, txnDate, invested, value);
        } catch (SQLIntegrityConstraintViolationException e) {
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.TRANSACTION_DUPLICATE);
        }
        log.info(Constants.Logs.TRANSACTION_CREATED, created.getId(), accountId, holdingId, txnDate, invested, value);
        return created(created);
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
