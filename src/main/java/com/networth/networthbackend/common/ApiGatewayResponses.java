package com.networth.networthbackend.common;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class ApiGatewayResponses {

    private static final Map<String, String> DEFAULT_HEADERS = Map.of(
            Constants.Http.Header.CONTENT_TYPE, Constants.Http.ContentType.APPLICATION_JSON,
            Constants.Http.Header.ACCESS_CONTROL_ALLOW_ORIGIN, Constants.Cors.ALLOW_ORIGIN,
            Constants.Http.Header.ACCESS_CONTROL_ALLOW_HEADERS, Constants.Cors.ALLOWED_HEADERS,
            Constants.Http.Header.ACCESS_CONTROL_ALLOW_METHODS, Constants.Cors.ALLOWED_METHODS
    );

    private static final ObjectMapper MAPPER = Jsons.MAPPER;

    public static APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(DEFAULT_HEADERS)
                .withBody(body);
    }

    public static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        try {
            return response(statusCode, MAPPER.writeValueAsString(body));
        } catch (Exception e) {
            return response(
                    Constants.Http.Status.INTERNAL_SERVER_ERROR,
                    jsonError(String.format(Constants.Messages.SERIALIZATION_ERROR_FMT, e.getMessage()))
            );
        }
    }

    public static APIGatewayProxyResponseEvent success(int statusCode, Object data) {
        return json(statusCode, ApiResponse.of(data));
    }

    public static APIGatewayProxyResponseEvent ok(Object data) {
        return success(Constants.Http.Status.OK, data);
    }

    public static APIGatewayProxyResponseEvent created(Object data) {
        return success(Constants.Http.Status.CREATED, data);
    }

    public static APIGatewayProxyResponseEvent error(int statusCode, String code, String message) {
        return json(statusCode, ApiError.of(code, message));
    }

    public static String jsonError(String message) {
        try {
            return MAPPER.createObjectNode().put(Constants.Api.ERROR_FIELD, message).toString();
        } catch (Exception ignored) {
            return "{\"" + Constants.Api.ERROR_FIELD + "\":\"" + message.replace("\"", "'") + "\"}";
        }
    }
}
