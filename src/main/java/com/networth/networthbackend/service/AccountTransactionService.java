package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.AccountTransaction;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountTransactionService extends Service<AccountTransaction> {

    public AccountTransactionService() {
        tableName = "account_transaction";
    }

    @Override
    public List<AccountTransaction> convertToListOfObject(ScanResult scanResult) {
        List<AccountTransaction> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            AccountTransaction accountTransaction = new AccountTransaction();
            accountTransaction.setId(valueMap.get("id").getS());
            accountTransaction.setAccountId(valueMap.get("accountId").getS());
            accountTransaction.setTimestamp(valueMap.get("timestamp").getS());
            accountTransaction.setCurrentBalance(Double.parseDouble(valueMap.get("currentBalance").getN()));
            accountTransaction.setPaid(valueMap.get("paid").getBOOL());
            dataList.add(accountTransaction);
        }
        return dataList;
    }

    @Override
    public APIGatewayProxyResponseEvent getAllByUniqueID(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransaction> accountTransactionClass) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(pathParams.get("id")));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("accountId = :val").withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);

        List<AccountTransaction> dataList = convertToListOfObject(scanResult);
        jsonBody = utility.convertListOfObjToString(dataList, context);
        context.getLogger().log("fetch" + accountTransactionClass.getName() + " List:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}
