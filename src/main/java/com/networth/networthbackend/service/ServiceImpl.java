package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.List;

public interface ServiceImpl<T, V> {

    APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type) throws IOException, URISyntaxException, InterruptedException, SQLException;

    APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type) throws IOException, URISyntaxException, InterruptedException, SQLException;

    APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> entityType);

    APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type);

    APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> type) throws SQLException;

    V convertRequestToEntity(T data);
}
