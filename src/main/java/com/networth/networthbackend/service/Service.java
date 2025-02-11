package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.Repository;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.List;
import java.util.Map;

public abstract class Service<V> implements ServiceImpl<V> {

    public final Utility<V> utilityRest = new Utility<>();
    public final Repository<V> repository;
    public List<String> secondaryFieldNameList;

    public Service(Repository<V> repository) {
        this.repository = repository;
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> entityType) {
        V requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        Integer id = repository.save(requestData);
        RestResponse restResponse = new RestResponse(String.valueOf(id));
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data added successfully to database:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> entityType) {
        V requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        Integer id = repository.update(requestData);
        RestResponse restResponse = new RestResponse(String.valueOf(id));
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data added successfully to database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        repository.delete(id);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data deleted successfully to database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<V> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<V> dataList = repository.getBySecondaryField(secondaryFieldNameList, id);
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public APIGatewayProxyResponseEvent createAPIResponse(String body, int statusCode, Map<String, String> headers) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setBody(body);
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(statusCode);
        return responseEvent;
    }
}
