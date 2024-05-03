package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.IncomeTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IncomeTagService extends Service<IncomeTag> {

    public IncomeTagService() {
        tableName = "income_tag";
    }

    @Override
    public List<IncomeTag> convertToListOfObject(ScanResult scanResult) {
        List<IncomeTag> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            IncomeTag incomeTag = new IncomeTag();
            incomeTag.setId(valueMap.get("id").getS());
            incomeTag.setUserId(valueMap.get("userId").getS());
            incomeTag.setName(valueMap.get("name").getS());
            incomeTag.setIsDefault(valueMap.get("isDefault").getBOOL());
            dataList.add(incomeTag);
        }
        return dataList;
    }

    @Override
    public APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeTag> incomeTagClass) {
        IncomeTag updatedIncomeTag = utility.convertStringToObj(apiGatewayRequest.getBody(), context, incomeTagClass);

        Map<String, String> expressionAttributesNames = new HashMap<>();
        expressionAttributesNames.put("#val1", "userId");
        expressionAttributesNames.put("#val2", "isDefault");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val1", new AttributeValue().withS(updatedIncomeTag.getUserId()));
        expressionAttributeValues.put(":val2", new AttributeValue().withBOOL(true));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("#val1 = :val1 AND #val2 = :val2").withExpressionAttributeNames(expressionAttributesNames).withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);

        List<IncomeTag> dataList = convertToListOfObject(scanResult);
        if (!dataList.isEmpty()) {
            IncomeTag oldIncomeTag = dataList.get(0);
            oldIncomeTag.setIsDefault(false);
            dynamoDBMapper.save(oldIncomeTag);
        }
        return super.update(apiGatewayRequest, context, incomeTagClass);
    }
}
