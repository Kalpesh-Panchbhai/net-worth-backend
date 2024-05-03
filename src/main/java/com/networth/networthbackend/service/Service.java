package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.AccountBroker;
import com.networth.networthbackend.model.DeleteResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Service<T> implements ServiceImpl<T> {

    public final Utility<T> utility = new Utility<>();
    public String tableName = "";
    String jsonBody = null;
    AmazonDynamoDB client;
    DynamoDBMapper dynamoDBMapper;

    public Service() {
        client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDBMapper = new DynamoDBMapper(client);
    }

    public APIGatewayProxyResponseEvent createAPIResponse(String body, int statusCode, Map<String, String> headers) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setBody(body);
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(statusCode);
        return responseEvent;
    }

    @Override
    public APIGatewayProxyResponseEvent add(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass) {
        T data = utility.convertStringToObj(apiGatewayRequest.getBody(), context, tClass);
        dynamoDBMapper.save(data);
        jsonBody = utility.convertObjToString(data, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass) {
        T data = utility.convertStringToObj(apiGatewayRequest.getBody(), context, tClass);
        dynamoDBMapper.save(data);
        jsonBody = utility.convertObjToString(data, context);
        context.getLogger().log("data updated successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();
        String key = pathParams.get("id");
        deleteDataByPrimaryField(key);

        DeleteResponse deleteResponse = new DeleteResponse(key);
        jsonBody = new Utility<DeleteResponse>().convertObjToString(deleteResponse, context);
        context.getLogger().log("data deleted successfully from dynamodb:::" + jsonBody);

        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public void deleteDataByPrimaryField(String primaryValue) {
        HashMap<String, AttributeValue> keyToDelete = new HashMap<>();
        keyToDelete.put("id", new AttributeValue().withS(primaryValue));
        DeleteItemRequest deleteItemRequest = new DeleteItemRequest().withTableName(tableName).withKey(keyToDelete);
        client.deleteItem(deleteItemRequest);
    }

    @Override
    public APIGatewayProxyResponseEvent getAllByUniqueID(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<T> tClass) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(pathParams.get("id")));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("userId = :val").withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);

        List<T> dataList = convertToListOfObject(scanResult);
        jsonBody = utility.convertListOfObjToString(dataList, context);
        context.getLogger().log("fetch" + tClass.getName() + " List:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<T> getAllByUniqueID(String fieldName, String fieldValue) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(fieldValue));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression(fieldName + " = :val").withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);

        return convertToListOfObject(scanResult);
    }
}
