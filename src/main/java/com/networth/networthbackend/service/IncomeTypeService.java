package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.IncomeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IncomeTypeService extends Service<IncomeType> {

    public IncomeTypeService() {
        tableName = "income_type";
    }

    @Override
    public List<IncomeType> convertToListOfObject(ScanResult scanResult) {
        List<IncomeType> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            IncomeType incomeType = new IncomeType();
            incomeType.setId(valueMap.get("id").getS());
            incomeType.setUserId(valueMap.get("userId").getS());
            incomeType.setName(valueMap.get("name").getS());
            incomeType.setIsDefault(valueMap.get("isDefault").getBOOL());
            dataList.add(incomeType);
        }
        return dataList;
    }

    @Override
    public APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeType> incomeTypeClass) {
        IncomeType updatedIncomeType = utility.convertStringToObj(apiGatewayRequest.getBody(), context, incomeTypeClass);

        Map<String, String> expressionAttributesNames = new HashMap<>();
        expressionAttributesNames.put("#val1", "userId");
        expressionAttributesNames.put("#val2", "isDefault");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val1", new AttributeValue().withS(updatedIncomeType.getUserId()));
        expressionAttributeValues.put(":val2", new AttributeValue().withBOOL(true));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("#val1 = :val1 AND #val2 = :val2").withExpressionAttributeNames(expressionAttributesNames).withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);

        List<IncomeType> dataList = convertToListOfObject(scanResult);
        if (!dataList.isEmpty()) {
            IncomeType oldIncomeType = dataList.get(0);
            oldIncomeType.setIsDefault(false);
            dynamoDBMapper.save(oldIncomeType);
        }
        return super.update(apiGatewayRequest, context, incomeTypeClass);
    }
}
