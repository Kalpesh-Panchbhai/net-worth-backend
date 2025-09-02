package com.networth.networthbackend.chartdata;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

@Slf4j
public class ChartDataApiHandler extends EntityHandlerBase {

    private final ChartDataRepository chartDataRepository = new ChartDataRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> getChartData(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.CHART_DATA, action));
        };
    }

    private APIGatewayProxyResponseEvent getChartData(APIGatewayProxyRequestEvent request) throws Exception {
        // Get query parameters
        String chartType = queryParam(request, Constants.Query.CHART_TYPE);
        String period = queryParam(request, Constants.Query.PERIOD);
        Long id = parsePathId(request);

        // Validate required parameters
        if (chartType == null || chartType.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_CHART_TYPE_REQUIRED);
        }

        if (!chartDataRepository.isValidChartType(chartType)) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_CHART_TYPE_INVALID);
        }

        if (period == null || period.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_PERIOD_REQUIRED);
        }

        if (!chartDataRepository.isValidPeriod(period)) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_PERIOD_INVALID);
        }

        if (id == null || id <= 0) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_ID_REQUIRED);
        }

        // Calculate start date based on period
        LocalDate startDate = chartDataRepository.calculateStartDate(period);

        // Get chart data based on chart type
        List<ChartDataPoint> chartData;
        switch (chartType.toLowerCase()) {
            case Constants.ChartData.CHART_TYPE_HOLDING -> {
                chartData = chartDataRepository.getChartDataForHolding(id, startDate);
                log.info("Chart data fetched for holding: id={}, period={}, points={}", id, period, chartData.size());
            }
            case Constants.ChartData.CHART_TYPE_ACCOUNT -> {
                chartData = chartDataRepository.getChartDataForAccount(id, startDate);
                log.info("Chart data fetched for account: id={}, period={}, points={}", id, period, chartData.size());
            }
            case Constants.ChartData.CHART_TYPE_WATCHLIST -> {
                chartData = chartDataRepository.getChartDataForWatchlist(id, startDate);
                log.info("Chart data fetched for watchlist: id={}, period={}, points={}", id, period, chartData.size());
            }
            default -> {
                return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.CHART_DATA_CHART_TYPE_INVALID);
            }
        }

        return ok(chartData);
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
