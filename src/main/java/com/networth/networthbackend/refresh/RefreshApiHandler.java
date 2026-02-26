package com.networth.networthbackend.refresh;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.tools.DailyDataRunner;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class RefreshApiHandler extends EntityHandlerBase {

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }

        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> refreshData();
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "refresh", action));
        };
    }

    private APIGatewayProxyResponseEvent refreshData() {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting manual data refresh");
            DailyDataRunner.run();
            long durationMs = System.currentTimeMillis() - startTime;

            log.info("Manual data refresh completed successfully in {} ms", durationMs);

            return ok(Map.of(
                    "message", "Data refreshed successfully",
                    "durationMs", durationMs
            ));

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Manual data refresh failed after {} ms: {}", durationMs, e.getMessage(), e);

            return badRequest(Constants.ErrorCodes.REFRESH_FAILED, "Data refresh failed: " + e.getMessage());
        }
    }
}
