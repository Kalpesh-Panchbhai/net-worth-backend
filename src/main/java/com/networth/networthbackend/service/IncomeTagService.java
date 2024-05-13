package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;
import com.networth.networthbackend.model.TypeRest;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeTagService extends Service<TypeRest, IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<TypeRest> typeRestList = getIncomeTagList(id);
        String jsonBody = utilityRest.convertListOfObjToString(typeRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<TypeRest> getIncomeTagList(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        List<TypeRest> typeRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            TypeRest typeRest = new TypeRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("name").getS(), attributeValueMap.get("isDefault").getBOOL());
            typeRestList.add(typeRest);
        }
        return typeRestList;
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        if (requestData.getIsDefault()) {
            makeOtherRecordNonDefault(requestData.getUserId());
        }
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        makeOtherRecordNonDefault(requestData.getUserId());
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public IncomeTagEntity convertRequestToEntity(TypeRest data) {
        return new IncomeTagEntity(data.getId(), data.getUserId(), data.getName(), data.getIsDefault());
    }

    private void makeOtherRecordNonDefault(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            if (attributeValueMap.get("isDefault").getBOOL()) {
                repository.update(new IncomeTagEntity(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("name").getS(), false));
            }
        }
    }
}
