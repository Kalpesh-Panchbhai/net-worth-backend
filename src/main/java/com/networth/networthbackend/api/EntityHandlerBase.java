package com.networth.networthbackend.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Jsons;
import com.networth.networthbackend.common.Constants;

public abstract class EntityHandlerBase implements EntityHandler {

    protected static final ObjectMapper MAPPER = Jsons.MAPPER;

    protected APIGatewayProxyResponseEvent ok(Object body) {
        return ApiGatewayResponses.ok(body);
    }

    protected APIGatewayProxyResponseEvent created(Object body) {
        return ApiGatewayResponses.created(body);
    }

    protected APIGatewayProxyResponseEvent badRequest(String code, String message) {
        return ApiGatewayResponses.error(Constants.Http.Status.BAD_REQUEST, code, message);
    }

    protected APIGatewayProxyResponseEvent notFound(String code, String message) {
        return ApiGatewayResponses.error(Constants.Http.Status.NOT_FOUND, code, message);
    }

    protected JsonNode getJsonBody(APIGatewayProxyRequestEvent request) throws Exception {
        if (request == null || request.getBody() == null || request.getBody().isBlank()) return null;
        return MAPPER.readTree(request.getBody());
    }

    protected String queryParam(APIGatewayProxyRequestEvent request, String name) {
        if (request == null || request.getQueryStringParameters() == null) return null;
        return request.getQueryStringParameters().get(name);
    }

    protected String pathParam(APIGatewayProxyRequestEvent request, String name) {
        if (request == null || request.getPathParameters() == null) return null;
        return request.getPathParameters().get(name);
    }

    @Override
    public abstract APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception;
}
