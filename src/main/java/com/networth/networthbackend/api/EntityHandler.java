package com.networth.networthbackend.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.AccountApiHandler;
import com.networth.networthbackend.chartdata.ChartDataApiHandler;
import com.networth.networthbackend.holdings.HoldingApiHandler;
import com.networth.networthbackend.incomes.IncomeApiHandler;
import com.networth.networthbackend.incomesources.IncomeSourceApiHandler;
import com.networth.networthbackend.incometags.IncomeTagApiHandler;
import com.networth.networthbackend.transactions.TransactionApiHandler;
import com.networth.networthbackend.users.UserApiHandler;
import com.networth.networthbackend.watchlists.AccountWatchlistApiHandler;
import com.networth.networthbackend.watchlists.WatchlistAccountApiHandler;
import com.networth.networthbackend.watchlists.WatchlistApiHandler;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EntityHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Extract entity type from path or query parameters
            String entityType = extractEntityType(request);
            String action = extractAction(request);
            
            log.info("Processing request for entityType: {}, action: {}", entityType, action);
            
            // Route to appropriate handler based on entity type
            EntityHandlerBase handler = getHandlerForEntity(entityType);
            if (handler == null) {
                log.warn("No handler found for entity type: {}", entityType);
                return ApiGatewayResponses.error(
                    Constants.Http.Status.BAD_REQUEST, 
                    Constants.ErrorCodes.VALIDATION_ERROR, 
                    "Unsupported entity type: " + entityType
                );
            }
            
            return handler.handle(action, request, context);
            
        } catch (Exception e) {
            log.error("Error processing request", e);
            return ApiGatewayResponses.error(
                Constants.Http.Status.INTERNAL_SERVER_ERROR, 
                Constants.ErrorCodes.INTERNAL_ERROR, 
                "Internal server error: " + e.getMessage()
            );
        }
    }
    
    private String extractEntityType(APIGatewayProxyRequestEvent request) {
        // Try to extract from path parameters first
        if (request.getPathParameters() != null && request.getPathParameters().containsKey("entity")) {
            return request.getPathParameters().get("entity");
        }
        
        // Try to extract from query parameters
        if (request.getQueryStringParameters() != null && request.getQueryStringParameters().containsKey("entityType")) {
            return request.getQueryStringParameters().get("entityType");
        }
        
        // Try to extract from resource path
        String resource = request.getResource();
        if (resource != null) {
            // Handle paths like /accounts/{id}, /holdings/{id}, etc.
            String[] pathParts = resource.split("/");
            if (pathParts.length > 1) {
                return pathParts[1]; // First part after the leading slash
            }
        }
        
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
                case "PUT" -> Constants.Actions.UPDATE;
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
            default -> null;
        };
    }
}
