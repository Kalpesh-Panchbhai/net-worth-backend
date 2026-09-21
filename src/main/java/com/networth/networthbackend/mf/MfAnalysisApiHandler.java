package com.networth.networthbackend.mf;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only mutual-fund analysis, computed at request time from stored NAV history.
 * <pre>
 *   GET /mf-analysis?action=categories
 *   GET /mf-analysis?action=leaderboard&subCategory=Large Cap&metric=cagr&horizon=5Y&limit=20
 *   GET /mf-analysis?action=fund&schemeCode=118269
 * </pre>
 * A bare {@code GET /mf-analysis} lists categories, so the browser can start there and drill in.
 */
@Slf4j
public class MfAnalysisApiHandler extends EntityHandlerBase {

    private static final int DEFAULT_LIMIT = 20;
    private static final String DEFAULT_METRIC = MfMetricKey.Metric.CAGR.code;
    private static final String DEFAULT_HORIZON = MfMetricKey.Horizon.Y5.code;

    private final MfAnalysisService service = new MfAnalysisService();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        // A GET with no explicit action arrives as the method-derived "get"; treat it as the landing
        // list so the endpoint is discoverable without knowing the action vocabulary up front.
        String resolved = (action == null || action.equalsIgnoreCase(Constants.Actions.GET)) ? "categories" : action.toLowerCase();

        return switch (resolved) {
            case "categories" -> ok(service.categories());
            case "leaderboard" -> leaderboard(request);
            case "table" -> table(request);
            case "fund" -> fund(request);
            case "nav" -> nav(request);
            case "portfolio" -> portfolio(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                    String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "mf-analysis", action));
        };
    }

    /** GET /mf-analysis?action=nav&schemeCode=118269 — the fund's NAV curve for charts/calculators. */
    private APIGatewayProxyResponseEvent nav(APIGatewayProxyRequestEvent request) throws Exception {
        String schemeCodeParam = queryParam(request, "schemeCode");
        if (schemeCodeParam == null || schemeCodeParam.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "schemeCode is required");
        }
        int schemeCode;
        try {
            schemeCode = Integer.parseInt(schemeCodeParam.trim());
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "schemeCode must be an integer");
        }
        MfAnalysisService.NavSeries series = service.navSeries(schemeCode);
        if (series == null) return notFound(Constants.ErrorCodes.NOT_FOUND, "No NAV history for scheme code " + schemeCode);
        return ok(series);
    }

    /** GET /mf-analysis?action=portfolio&userId=1 — the user's held funds, ranked, for the X-ray. */
    private APIGatewayProxyResponseEvent portfolio(APIGatewayProxyRequestEvent request) throws Exception {
        String userIdParam = queryParam(request, "userId");
        if (userIdParam == null || userIdParam.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "userId is required");
        }
        long userId;
        try {
            userId = Long.parseLong(userIdParam.trim());
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "userId must be a number");
        }
        return ok(service.portfolio(userId));
    }

    /**
     * A sortable grid of funds with every column metric at one horizon. Omit {@code subCategory} for
     * all live funds, or pass one to scope to a category.
     * <pre>
     *   GET /mf-analysis?action=table&horizon=5Y
     *   GET /mf-analysis?action=table&subCategory=Large Cap&horizon=5Y
     * </pre>
     */
    private APIGatewayProxyResponseEvent table(APIGatewayProxyRequestEvent request) throws Exception {
        String subCategory = queryParam(request, "subCategory");
        String horizon = orDefault(queryParam(request, "horizon"), DEFAULT_HORIZON);
        try {
            return ok(service.table(subCategory, horizon));
        } catch (IllegalArgumentException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent leaderboard(APIGatewayProxyRequestEvent request) throws Exception {
        String subCategory = queryParam(request, "subCategory");
        if (subCategory == null || subCategory.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "subCategory is required");
        }
        String metric = orDefault(queryParam(request, "metric"), DEFAULT_METRIC);
        String horizon = orDefault(queryParam(request, "horizon"), DEFAULT_HORIZON);

        int limit = DEFAULT_LIMIT;
        String limitParam = queryParam(request, "limit");
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Integer.parseInt(limitParam.trim());
            } catch (NumberFormatException e) {
                return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "limit must be an integer");
            }
        }

        try {
            return ok(service.leaderboard(subCategory, metric, horizon, limit));
        } catch (IllegalArgumentException e) {
            // Unknown metric or horizon code — the message names the offending value.
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent fund(APIGatewayProxyRequestEvent request) throws Exception {
        String schemeCodeParam = queryParam(request, "schemeCode");
        if (schemeCodeParam == null || schemeCodeParam.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "schemeCode is required");
        }
        int schemeCode;
        try {
            schemeCode = Integer.parseInt(schemeCodeParam.trim());
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "schemeCode must be an integer");
        }

        MfAnalysisService.FundDetail detail = service.fund(schemeCode);
        if (detail == null) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, "No fund with NAV history for scheme code " + schemeCode);
        }
        return ok(detail);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
