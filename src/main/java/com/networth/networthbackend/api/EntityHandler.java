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
import com.networth.networthbackend.mf.MfAnalysisApiHandler;
import com.networth.networthbackend.stock.StockAnalysisApiHandler;
import com.networth.networthbackend.stock.StockPositionApiHandler;
import com.networth.networthbackend.stock.StockAlertApiHandler;
import com.networth.networthbackend.refresh.RefreshApiHandler;
import com.networth.networthbackend.transactions.TransactionApiHandler;
import com.networth.networthbackend.userconfig.UserConfigApiHandler;
import com.networth.networthbackend.users.UserApiHandler;
import com.networth.networthbackend.watchlists.AccountWatchlistApiHandler;
import com.networth.networthbackend.watchlists.WatchlistAccountApiHandler;
import com.networth.networthbackend.watchlists.WatchlistApiHandler;
import com.networth.networthbackend.syncmf.SyncMfApiHandler;
import com.networth.networthbackend.tools.YahooSearchApiHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class EntityHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    /**
     * Handlers are stateless (they hold only repositories and converters), so one instance each is
     * built when the class loads and shared across requests. Constructing them per request also
     * rebuilt every repository and HTTP client on every call.
     */
    private static final Map<String, EntityHandlerBase> HANDLERS = handlers();

    private static Map<String, EntityHandlerBase> handlers() {
        Map<String, EntityHandlerBase> map = new HashMap<>();
        register(map, AccountApiHandler::new, "accounts", "account");
        register(map, ChartDataApiHandler::new, "chart-data", "chartdata");
        register(map, HoldingApiHandler::new, "holdings", "holding");
        register(map, IncomeApiHandler::new, "incomes", "income");
        register(map, IncomeSourceApiHandler::new, "income-sources", "incomesources");
        register(map, IncomeTagApiHandler::new, "income-tags", "incometags");
        register(map, TransactionApiHandler::new, "transactions", "transaction");
        register(map, UserApiHandler::new, "users", "user");
        register(map, UserConfigApiHandler::new, "user-config", "userconfig");
        register(map, AccountWatchlistApiHandler::new, "account-watchlists", "accountwatchlists");
        register(map, WatchlistAccountApiHandler::new, "watchlist-accounts", "watchlistaccounts");
        register(map, WatchlistApiHandler::new, "watchlists", "watchlist");
        register(map, RefreshApiHandler::new, "refresh");
        register(map, YahooSearchApiHandler::new, "yahoo-search", "yahoosearch");
        register(map, SyncMfApiHandler::new, "sync-mf", "syncmf");
        register(map, MfAnalysisApiHandler::new, "mf-analysis", "mfanalysis");
        register(map, StockAnalysisApiHandler::new, "stock-analysis", "stockanalysis");
        register(map, StockPositionApiHandler::new, "stock-portfolio", "stockportfolio", "stock-positions");
        register(map, StockAlertApiHandler::new, "stock-alerts", "stockalerts");
        return Map.copyOf(map);
    }

    /**
     * Builds one handler and registers it under its aliases. A constructor that throws is logged
     * and skipped: because this runs in a static initialiser, letting it escape would become an
     * {@link ExceptionInInitializerError} that {@link #handleRequest}'s {@code catch (Exception)}
     * cannot catch, taking every other endpoint down with it for the container's whole lifetime.
     * The affected routes then return "unsupported entity type" instead.
     */
    private static void register(Map<String, EntityHandlerBase> map, Supplier<EntityHandlerBase> factory, String... aliases) {
        try {
            EntityHandlerBase handler = factory.get();
            for (String alias : aliases) map.put(alias, handler);
        } catch (RuntimeException | LinkageError e) {
            log.error("Could not construct the handler for {}; those routes will be unavailable", String.join(", ", aliases), e);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Request details - Path: {}, Resource: {}, HttpMethod: {}, PathParams: {}, QueryParams: {}",
                        request.getPath(), request.getResource(), request.getHttpMethod(),
                        request.getPathParameters(), request.getQueryStringParameters());
            }

            // Extract entity type from path or resource ONLY (do not use query params to avoid conflicts)
            String entityType = extractEntityType(request);
            String action = extractAction(request);

            log.debug("Processing request for entityType: {}, action: {}", entityType, action);

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
        if (entityType == null) return null;
        return HANDLERS.get(entityType.toLowerCase());
    }
}
