package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeRepository;
import com.networth.networthbackend.entity.IncomeEntity;
import com.networth.networthbackend.model.IncomeRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeService extends Service<IncomeRest, IncomeEntity> {

    public IncomeService() {
        super(new IncomeRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<IncomeRest> incomeRestList = getIncomeList(id);
        String jsonBody = utilityRest.convertListOfObjToString(incomeRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<IncomeRest> getIncomeList(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        List<IncomeRest> incomeRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            IncomeRest incomeRest = new IncomeRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), Double.parseDouble(attributeValueMap.get("amount").getN()), Double.parseDouble(attributeValueMap.get("taxPaid").getN()), attributeValueMap.get("creditedOn").getS(), attributeValueMap.get("currency").getS(), attributeValueMap.get("incomeType").getS(), attributeValueMap.get("incomeTag").getS());
            incomeRestList.add(incomeRest);
        }
        return incomeRestList;
    }

    @Override
    public IncomeEntity convertRequestToEntity(IncomeRest data) {
        return new IncomeEntity(data.getId(), data.getUserId(), data.getAmount(), data.getTaxPaid(), data.getCreditedOn(), data.getCurrency(), data.getIncomeType(), data.getIncomeTag());
    }
}
