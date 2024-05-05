package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.List;

public interface ServiceImpl<T, V> {

    APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type);

    APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type);

    APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> entityType);

    APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type);

    V convertRequestToEntity(T data);
}
