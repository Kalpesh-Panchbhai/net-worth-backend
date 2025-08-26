package com.networth.networthbackend.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.BaseApiHandler;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.users.UserApiHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class ApiRouterHandler extends BaseApiHandler {

    private final Map<String, EntityHandler> registry = new HashMap<>();

    public ApiRouterHandler() {
        // Register entity handlers
        registry.put(Constants.Entities.USERS, new UserApiHandler());
        // Future: registry.put("income-tags", new IncomeTagApiHandler());
    }

    @Override
    protected APIGatewayProxyResponseEvent doHandle(APIGatewayProxyRequestEvent request, Context context) throws Exception {
        String path = request.getPath();
        String method = request.getHttpMethod();
        log.info(Constants.Logs.ROUTER_RECEIVED, method, path);

        Route route = parseRoute(request);
        if (route == null || route.entity == null) {
            return ApiGatewayResponses.error(
                    Constants.Http.Status.NOT_FOUND,
                    Constants.ErrorCodes.ROUTE_NOT_FOUND,
                    Constants.Messages.UNKNOWN_ROUTE
            );
        }
        EntityHandler handler = registry.get(route.entity.toLowerCase(Locale.ROOT));
        if (handler == null) {
            return ApiGatewayResponses.error(
                    Constants.Http.Status.NOT_FOUND,
                    Constants.ErrorCodes.ENTITY_NOT_SUPPORTED,
                    String.format(Constants.Messages.ENTITY_NOT_SUPPORTED_FMT, route.entity)
            );
        }
        String action = inferAction(method);
        if (action == null) {
            return ApiGatewayResponses.error(
                    Constants.Http.Status.BAD_REQUEST,
                    Constants.ErrorCodes.ACTION_REQUIRED,
                    Constants.Messages.ACTION_COULD_NOT_BE_INFERRED
            );
        }
        log.debug(Constants.Logs.DISPATCH_ENTITY_ACTION, route.entity, action);
        return handler.handle(action, request, context);
    }

    private String inferAction(String method) {
        if (Constants.Http.Method.POST.equalsIgnoreCase(method)) return Constants.Actions.CREATE;
        if (Constants.Http.Method.GET.equalsIgnoreCase(method)) return Constants.Actions.GET;
        if (Constants.Http.Method.PUT.equalsIgnoreCase(method)) return Constants.Actions.UPDATE;
        if (Constants.Http.Method.DELETE.equalsIgnoreCase(method)) return Constants.Actions.DELETE;
        return null;
    }

    private Route parseRoute(APIGatewayProxyRequestEvent request) {
        // RESTful only: path must be /{entity}; action inferred from HTTP method
        String path = request.getPath();
        if (path == null || path.isBlank()) return null;
        String p = path.startsWith(Constants.Http.PATH_SEPARATOR) ? path.substring(1) : path;
        String[] parts = p.split(Constants.Http.PATH_SEPARATOR);
        if (parts.length == 1 && !parts[0].isBlank()) {
            return new Route(parts[0]);
        }
        // Any extra segments are not supported
        return null;
    }

    private record Route(String entity) {
    }
}
