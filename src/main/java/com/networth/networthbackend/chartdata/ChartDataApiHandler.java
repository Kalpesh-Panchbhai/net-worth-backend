package com.networth.networthbackend.chartdata;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ChartDataApiHandler extends EntityHandlerBase {

    private final ChartDataService service = new ChartDataService();
    private final ChartDataValidationService validationService = new ChartDataValidationService();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }

        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> getChartData(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "chartdata", action));
        };
    }

    private APIGatewayProxyResponseEvent getChartData(APIGatewayProxyRequestEvent request) throws Exception {
        // Parse request parameters
        String entityTypeParam = queryParam(request, "entityType");
        String entityIdParam = queryParam(request, "entityId");
        String timePeriod = queryParam(request, "timePeriod");

        // Basic parameter validation (no entity existence check)
        ChartDataValidationService.ValidationResult validationResult = validationService.validateChartDataParameters(entityTypeParam, entityIdParam, timePeriod);

        if (!validationResult.valid()) {
            log.warn("Chart data request validation failed: {}", validationService.getValidationSummary(validationResult));
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, validationResult.getFirstError());
        }

        // Extract validated parameters
        EntityType entityType = EntityType.fromCode(entityTypeParam).get(); // Safe since validation passed
        long entityId = Long.parseLong(entityIdParam); // Safe since validation passed

        // Get chart data using generic method
        List<ChartData> chartData;
        try {
            log.debug("Fetching chart data for {}: entityId={}, timePeriod={}", entityType.getCode(), entityId, timePeriod);

            chartData = service.getChartData(entityType, entityId, timePeriod);

            log.info("Successfully fetched {} chart data points for {} with ID {}", chartData.size(), entityType.getCode(), entityId);

            return ok(chartData);

        } catch (Exception e) {
            log.error("Error fetching chart data for {} with ID {}: {}", entityType.getCode(), entityId, e.getMessage(), e);
            throw e;
        }
    }
}
