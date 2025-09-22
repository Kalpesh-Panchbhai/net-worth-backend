package com.networth.networthbackend.common;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    protected static final ObjectMapper MAPPER = Jsons.MAPPER;

    @Override
    public final APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            return doHandle(request, context);
        } catch (Exception e) {
            log.error(Constants.Logs.UNHANDLED_ERROR, e);
            return ApiGatewayResponses.error(
                    Constants.Http.Status.INTERNAL_SERVER_ERROR,
                    Constants.ErrorCodes.INTERNAL_ERROR,
                    Constants.Messages.INTERNAL_ERROR
            );
        }
    }

    protected abstract APIGatewayProxyResponseEvent doHandle(APIGatewayProxyRequestEvent request, Context context) throws Exception;

    // -------- Helpers

    protected APIGatewayProxyResponseEvent ok(Object body) {
        return ApiGatewayResponses.ok(body);
    }

    protected APIGatewayProxyResponseEvent created(Object body) {
        return ApiGatewayResponses.created(body);
    }

    protected APIGatewayProxyResponseEvent badRequest(String code, String message) {
        return ApiGatewayResponses.error(400, code, message);
    }

    protected JsonNode getJsonBody(APIGatewayProxyRequestEvent request) throws Exception {
        if (request == null || request.getBody() == null || request.getBody().isBlank()) return null;
        return MAPPER.readTree(request.getBody());
    }

    protected String queryParam(APIGatewayProxyRequestEvent request, String name) {
        if (request == null || request.getQueryStringParameters() == null) return null;
        return request.getQueryStringParameters().get(name);
    }
}
