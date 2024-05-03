package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.IncomeType;

import java.util.List;

public interface ServiceImpl<T> {

    APIGatewayProxyResponseEvent save(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    APIGatewayProxyResponseEvent getAllByUserID(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    List<T> convertToListOfObject(ScanResult scanResult);
}
