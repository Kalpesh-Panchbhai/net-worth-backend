package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.List;

public interface ServiceImpl<T> {

    APIGatewayProxyResponseEvent add(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    APIGatewayProxyResponseEvent getAllByUniqueID(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass);

    List<T> getAllByUniqueID(String fieldName, String fieldValue);

    APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context);

    void deleteDataByPrimaryField(String primaryValue);

    List<T> convertToListOfObject(ScanResult scanResult);
}
