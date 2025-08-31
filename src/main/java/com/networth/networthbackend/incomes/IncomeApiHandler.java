package com.networth.networthbackend.incomes;

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
import java.util.List;

@Slf4j
public class IncomeApiHandler extends EntityHandlerBase {

    private final IncomeRepository repository = new IncomeRepository();

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
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.INCOMES, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long userId = json.hasNonNull(Constants.Query.USER_ID) ? json.get(Constants.Query.USER_ID).asLong() : null;
        Long sourceId = json.hasNonNull(Constants.Query.INCOME_SOURCE_ID) ? json.get(Constants.Query.INCOME_SOURCE_ID).asLong() : null;
        Long tagId = json.hasNonNull(Constants.Query.INCOME_TAG_ID) ? json.get(Constants.Query.INCOME_TAG_ID).asLong() : null;
        BigDecimal netAmount = json.hasNonNull(Constants.Query.NET_AMOUNT) ? json.get(Constants.Query.NET_AMOUNT).decimalValue() : null;
        BigDecimal taxPaid = json.hasNonNull(Constants.Query.TAX_PAID) ? json.get(Constants.Query.TAX_PAID).decimalValue() : BigDecimal.ZERO;
        String currency = json.hasNonNull(Constants.Query.CURRENCY) ? json.get(Constants.Query.CURRENCY).asText() : null;
        String creditedDateStr = json.hasNonNull(Constants.Query.CREDITED_DATE) ? json.get(Constants.Query.CREDITED_DATE).asText() : null;
        if (userId == null || userId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_USER_ID_REQUIRED);
        }
        if (sourceId == null || sourceId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_SOURCE_ID_REQUIRED);
        }
        if (tagId == null || tagId <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_TAG_ID_REQUIRED);
        }
        if (netAmount == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_NET_AMOUNT_REQUIRED);
        }
        if (currency == null || currency.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_CURRENCY_REQUIRED);
        }
        if (creditedDateStr == null || creditedDateStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_CREDITED_DATE_REQUIRED);
        }
        LocalDate creditedDate;
        try {
            creditedDate = LocalDate.parse(creditedDateStr);
        } catch (Exception e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_CREDITED_DATE_REQUIRED);
        }
        log.debug(Constants.Logs.INCOME_CREATE, userId, sourceId, tagId, netAmount, taxPaid, currency, creditedDate);
        Income created;
        try {
            created = repository.create(userId, sourceId, tagId, netAmount, taxPaid, currency, creditedDate);
        } catch (SQLIntegrityConstraintViolationException e) {
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.INCOME_DUPLICATE);
        }
        return created(created);
    }

    private APIGatewayProxyResponseEvent listByUserId(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.INCOMES_LIST, userId);
        List<Income> incomes = repository.listByUserId(userId);
        log.info(Constants.Logs.INCOMES_LIST_COUNT, incomes.size(), userId);
        return ok(incomes);
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

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = repository.deleteById(id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.INCOME_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long incomeSourceId = json.has(Constants.Query.INCOME_SOURCE_ID) && !json.get(Constants.Query.INCOME_SOURCE_ID).isNull() ? json.get(Constants.Query.INCOME_SOURCE_ID).asLong() : null;
        Long incomeTagId = json.has(Constants.Query.INCOME_TAG_ID) && !json.get(Constants.Query.INCOME_TAG_ID).isNull() ? json.get(Constants.Query.INCOME_TAG_ID).asLong() : null;
        java.math.BigDecimal netAmount = json.has(Constants.Query.NET_AMOUNT) && !json.get(Constants.Query.NET_AMOUNT).isNull() ? json.get(Constants.Query.NET_AMOUNT).decimalValue() : null;
        java.math.BigDecimal taxPaid = json.has(Constants.Query.TAX_PAID) && !json.get(Constants.Query.TAX_PAID).isNull() ? json.get(Constants.Query.TAX_PAID).decimalValue() : null;
        String currency = json.has(Constants.Query.CURRENCY) && !json.get(Constants.Query.CURRENCY).isNull() ? json.get(Constants.Query.CURRENCY).asText() : null;
        String creditedDateStr = json.has(Constants.Query.CREDITED_DATE) && !json.get(Constants.Query.CREDITED_DATE).isNull() ? json.get(Constants.Query.CREDITED_DATE).asText() : null;

        if (incomeSourceId == null && incomeTagId == null && netAmount == null && taxPaid == null && currency == null && creditedDateStr == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_UPDATE_NO_FIELDS);
        }

        java.time.LocalDate creditedDate = null;
        if (creditedDateStr != null) {
            try {
                creditedDate = java.time.LocalDate.parse(creditedDateStr);
            } catch (Exception e) {
                return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.INCOME_CREDITED_DATE_REQUIRED);
            }
        }

        int affected = repository.updateById(id, incomeSourceId, incomeTagId, netAmount, taxPaid, currency, creditedDate);
        if (affected == 0) {
            // Either no fields or not found. Check existence to return 404 vs validation already handled
            var maybe = repository.findById(id);
            if (maybe.isEmpty()) return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.INCOME_NOT_FOUND);
        }
        var updated = repository.findById(id).orElse(null);
        if (updated == null) return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.INCOME_NOT_FOUND);
        return ok(updated);
    }
}
