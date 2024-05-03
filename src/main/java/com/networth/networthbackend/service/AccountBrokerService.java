package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.AccountBroker;
import com.networth.networthbackend.entity.AccountTransaction;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountBrokerService extends Service<AccountBroker> {

    public AccountBrokerService() {
        tableName = "account_broker";
    }

    @Override
    public List<AccountBroker> convertToListOfObject(ScanResult scanResult) {
        List<AccountBroker> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            AccountBroker accountBroker = new AccountBroker();
            accountBroker.setId(valueMap.get("id").getS());
            accountBroker.setAccountId(valueMap.get("accountId").getS());
            accountBroker.setName(valueMap.get("name").getS());
            accountBroker.setSymbol(valueMap.get("symbol").getS());
            accountBroker.setTimestamp("");
            accountBroker.setCurrentUnit(Double.parseDouble(valueMap.get("currentUnit").getN()));
            dataList.add(accountBroker);
        }
        return dataList;
    }

    @Override
    public APIGatewayProxyResponseEvent add(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBroker> accountBrokerClass) {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = super.add(apiGatewayRequest, context, accountBrokerClass);

        AccountBroker account = utility.convertStringToObj(apiGatewayProxyResponseEvent.getBody(), context, AccountBroker.class);

        if (!account.getTimestamp().isBlank()) {
            AccountTransactionService accountTransactionService = new AccountTransactionService();
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            AccountTransaction accountTransaction = new AccountTransaction(null, account.getId(), account.getTimestamp(), account.getCurrentUnit(), true, "UPDATE");
            apiGatewayProxyRequestEvent.setBody(accountTransactionService.utility.convertObjToString(accountTransaction, context));
            accountTransactionService.add(apiGatewayProxyRequestEvent, context, AccountTransaction.class);
        }
        return apiGatewayProxyResponseEvent;
    }

    @Override
    public APIGatewayProxyResponseEvent getAllByUniqueID(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBroker> accountBrokerClass) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();

        List<AccountBroker> dataList = getAllByUniqueID("accountId", pathParams.get("id"));
        jsonBody = utility.convertListOfObjToString(dataList, context);
        context.getLogger().log("fetch" + accountBrokerClass.getName() + " List:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        List<AccountTransaction> accountTransactionList = accountTransactionService.getAllByUniqueID("accountId", pathParams.get("id"));
        for (AccountTransaction accountTransaction : accountTransactionList) {
            accountTransactionService.deleteDataByPrimaryField(accountTransaction.getId());
        }
        return super.delete(apiGatewayRequest, context);
    }
}
