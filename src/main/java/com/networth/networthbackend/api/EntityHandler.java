package com.networth.networthbackend.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.AccountApiHandler;
import com.networth.networthbackend.chartdata.ChartDataApiHandler;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.holdings.HoldingApiHandler;
import com.networth.networthbackend.incomes.IncomeApiHandler;
import com.networth.networthbackend.incomesources.IncomeSourceApiHandler;
import com.networth.networthbackend.incometags.IncomeTagApiHandler;
import com.networth.networthbackend.refresh.RefreshApiHandler;
import com.networth.networthbackend.transactions.TransactionApiHandler;
import com.networth.networthbackend.users.UserApiHandler;
import com.networth.networthbackend.watchlists.AccountWatchlistApiHandler;
import com.networth.networthbackend.watchlists.WatchlistAccountApiHandler;
import com.networth.networthbackend.watchlists.WatchlistApiHandler;
import com.networth.networthbackend.tools.YahooSearchApiHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EntityHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Debug logging to understand the request structure
            log.info("Request details - Path: {}, Resource: {}, HttpMethod: {}", request.getPath(), request.getResource(), request.getHttpMethod());
            log.info("Path parameters: {}", request.getPathParameters());
            log.info("Query parameters: {}", request.getQueryStringParameters());

            // Extract entity type from path or resource ONLY (do not use query params to avoid conflicts)
            String entityType = extractEntityType(request);
            String action = extractAction(request);

            log.info("Processing request for entityType: {}, action: {}", entityType, action);

            // Route to appropriate handler based on entity type
            EntityHandlerBase handler = getHandlerForEntity(entityType);
            if (handler == null) {
                log.warn("No handler found for entity type: {}", entityType);
                return ApiGatewayResponses.error(Constants.Http.Status.BAD_REQUEST, Constants.ErrorCodes.VALIDATION_ERROR, "Unsupported entity type: " + entityType);
            }

            return handler.handle(action, request, context);

        } catch (Exception e) {
            log.error("Error processing request", e);
            return ApiGatewayResponses.error(Constants.Http.Status.INTERNAL_SERVER_ERROR, Constants.ErrorCodes.INTERNAL_ERROR, "Internal server error: " + e.getMessage());
        }
    }

    private String extractEntityType(APIGatewayProxyRequestEvent request) {
        // 1) Prefer the actual path first: /{entity} or /{entity}/{id}
        String path = request.getPath();
        if (path != null && !path.isEmpty()) {
            String[] pathParts = path.split("/");
            for (String part : pathParts) {
                if (!part.isEmpty()) {
                    log.debug("Entity type from path: {}", part);
                    return part;
                }
            }
        }

        // 2) Fallback to API Gateway resource template if available
        String resource = request.getResource();
        if (resource != null && !resource.isEmpty()) {
            String[] pathParts = resource.split("/");
            for (String part : pathParts) {
                if (!part.isEmpty() && !part.startsWith("{") && !part.endsWith("}")) {
                    log.debug("Entity type from resource: {}", part);
                    return part;
                }
            }
        }

        // 3) Final fallback: explicit path parameter named 'entity' if your API defines it
        if (request.getPathParameters() != null && request.getPathParameters().containsKey("entity")) {
            String entity = request.getPathParameters().get("entity");
            log.debug("Entity type from path parameters: {}", entity);
            return entity;
        }

        // IMPORTANT: We intentionally do NOT read query parameter 'entityType' here to avoid conflicts
        // with endpoints like /chart-data that legitimately use 'entityType' as a business parameter.

        log.warn("Could not extract entity type from request. Path: {}, Resource: {}, PathParams: {}", request.getPath(), request.getResource(), request.getPathParameters());
        return null;
    }

    private String extractAction(APIGatewayProxyRequestEvent request) {
        // Try to extract from query parameters first
        if (request.getQueryStringParameters() != null && request.getQueryStringParameters().containsKey("action")) {
            return request.getQueryStringParameters().get("action");
        }

        // Map HTTP methods to actions
        String httpMethod = request.getHttpMethod();
        if (httpMethod != null) {
            return switch (httpMethod.toUpperCase()) {
                case "GET" -> Constants.Actions.GET;
                case "POST" -> Constants.Actions.CREATE;
                case "PUT", "PATCH" -> Constants.Actions.UPDATE;
                case "DELETE" -> Constants.Actions.DELETE;
                default -> null;
            };
        }

        return null;
    }

    private EntityHandlerBase getHandlerForEntity(String entityType) {
        if (entityType == null) {
            return null;
        }

        return switch (entityType.toLowerCase()) {
            case "accounts", "account" -> new AccountApiHandler();
            case "chart-data", "chartdata" -> new ChartDataApiHandler();
            case "holdings", "holding" -> new HoldingApiHandler();
            case "incomes", "income" -> new IncomeApiHandler();
            case "income-sources", "incomesources" -> new IncomeSourceApiHandler();
            case "income-tags", "incometags" -> new IncomeTagApiHandler();
            case "transactions", "transaction" -> new TransactionApiHandler();
            case "users", "user" -> new UserApiHandler();
            case "account-watchlists", "accountwatchlists" -> new AccountWatchlistApiHandler();
            case "watchlist-accounts", "watchlistaccounts" -> new WatchlistAccountApiHandler();
            case "watchlists", "watchlist" -> new WatchlistApiHandler();
            case "refresh" -> new RefreshApiHandler();
            case "yahoo-search", "yahoosearch" -> new YahooSearchApiHandler();
            default -> null;
        };
    }
}
