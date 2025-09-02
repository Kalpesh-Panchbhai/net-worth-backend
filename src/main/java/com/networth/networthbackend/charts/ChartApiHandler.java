package com.networth.networthbackend.charts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

@Slf4j
public class ChartApiHandler extends EntityHandlerBase {

    private final ChartRepository repository = new ChartRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> get(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.CHARTS, action));
        };
    }

    private APIGatewayProxyResponseEvent get(APIGatewayProxyRequestEvent request) throws Exception {
        String accountIdStr = queryParam(request, Constants.Query.ACCOUNT_ID);
        String holdingIdStr = queryParam(request, Constants.Query.HOLDING_ID);
        if ((accountIdStr == null || accountIdStr.isBlank()) && (holdingIdStr == null || holdingIdStr.isBlank())) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_FILTER_REQUIRED);
        }
        String range = queryParam(request, Constants.Query.RANGE);
        if (range == null || range.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "range is required and must be one of 1M,3M,6M,1Y,2Y,5Y,ALL");
        }
        range = range.toUpperCase();
        LocalDate to = LocalDate.now();
        LocalDate from = null;
        switch (range) {
            case "1M" -> from = to.minusMonths(1);
            case "3M" -> from = to.minusMonths(3);
            case "6M" -> from = to.minusMonths(6);
            case "1Y" -> from = to.minusYears(1);
            case "2Y" -> from = to.minusYears(2);
            case "5Y" -> from = to.minusYears(5);
            case "ALL" -> to = null;
            default -> {
                return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "range must be one of 1M,3M,6M,1Y,2Y,5Y,ALL");
            }
        }

        if (holdingIdStr != null && !holdingIdStr.isBlank()) {
            long holdingId = Long.parseLong(holdingIdStr);
            log.debug("Listing chart points by holdingId={}, range={}, from={}, to={}", holdingId, range, from, to);
            List<ChartPoint> list = repository.listForHolding(holdingId, from, to);
            log.info("Chart points fetched: count={} for holdingId={} range={} from={} to={}", list.size(), holdingId, range, from, to);
            return ok(list);
        }
        long accountId = Long.parseLong(accountIdStr);
        log.debug("Listing chart points by accountId={}, range={}, from={}, to={}", accountId, range, from, to);
        List<ChartPoint> list = repository.listForAccount(accountId, from, to);
        log.info("Chart points fetched: count={} for accountId={} range={} from={} to={}", list.size(), accountId, range, from, to);
        return ok(list);
    }
}
