package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTypeRepository;
import com.networth.networthbackend.entity.IncomeTypeEntity;
import com.networth.networthbackend.model.TypeRest;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeTypeService extends Service<TypeRest, IncomeTypeEntity> {

    public IncomeTypeService() {
        super(new IncomeTypeRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<TypeRest> typeRestList = getIncomeTypeList(id);
        String jsonBody = utilityRest.convertListOfObjToString(typeRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws SQLException {
        return null;
    }

    public List<TypeRest> getIncomeTypeList(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        List<TypeRest> typeRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            TypeRest typeRest = new TypeRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("name").getS(), attributeValueMap.get("isDefault").getBOOL());
            typeRestList.add(typeRest);
        }
        return typeRestList;
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException, SQLException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        if(requestData.getIsDefault()) {
            makeOtherRecordNonDefault(requestData.getUserId());
        }
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException, SQLException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        makeOtherRecordNonDefault(requestData.getUserId());
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public IncomeTypeEntity convertRequestToEntity(TypeRest data) {
        return new IncomeTypeEntity(data.getId(), data.getUserId(), data.getName(), data.getIsDefault());
    }

    private void makeOtherRecordNonDefault(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            if (attributeValueMap.get("isDefault").getBOOL()) {
                repository.update(new IncomeTypeEntity(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("name").getS(), false));
            }
        }
    }
}
