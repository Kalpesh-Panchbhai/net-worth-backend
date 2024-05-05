package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountTransactionRepository;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.model.AccountTransactionRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountTransactionService extends Service<AccountTransactionRest, AccountTransactionEntity> {

    public AccountTransactionService() {
        super(new AccountTransactionRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("accountId", id);
        List<AccountTransactionRest> accountTransactionRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountTransactionRest accountTransactionRestRequest = new AccountTransactionRest(attributeValueMap.get("id").getS(),
                    attributeValueMap.get("accountId").getS(),
                    attributeValueMap.get("timestamp").getS(),
                    Double.parseDouble(attributeValueMap.get("currentBalance").getN()),
                    attributeValueMap.get("paid").getBOOL(),
                    "");
            accountTransactionRestList.add(accountTransactionRestRequest);
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountTransactionRestList, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public AccountTransactionEntity convertRequestToEntity(AccountTransactionRest data) {
        AccountTransactionEntity accountTransactionEntity = new AccountTransactionEntity();
        accountTransactionEntity.setId(data.getId());
        accountTransactionEntity.setAccountId(data.getAccountId());
        accountTransactionEntity.setTimestamp(data.getTimestamp());
        accountTransactionEntity.setCurrentBalance(data.getAmount());
        accountTransactionEntity.setPaid(data.getPaid());
        return accountTransactionEntity;
    }
}