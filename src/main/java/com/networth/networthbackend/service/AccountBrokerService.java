package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.model.AccountBrokerRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountBrokerService extends Service<AccountBrokerRest, AccountBrokerEntity> {

    private final AccountTransactionService accountTransactionService = new AccountTransactionService();
    private BalanceRest balanceRest = new BalanceRest(0.0, 0.0);
    private Double currentUnit;

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("accountId", id);
        List<AccountBrokerRest> accountBrokerRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            fetchBalanceDetails(attributeValueMap.get("id").getS());
            AccountBrokerRest accountRestResponse = new AccountBrokerRest(attributeValueMap.get("id").getS(), attributeValueMap.get("accountId").getS(), attributeValueMap.get("symbol").getS(), attributeValueMap.get("name").getS(), currentUnit, balanceRest);
            accountBrokerRestList.add(accountRestResponse);
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountBrokerRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    private void fetchBalanceDetails(String accountId) {
        ScanResult scanResult = accountTransactionService.repository.getBySecondaryField("accountId", accountId);
        balanceRest = new BalanceRest(0.0, 0.0);
        currentUnit = 0.0;
        if (scanResult.getCount() > 0) {
            List<Map<String, AttributeValue>> map = scanResult.getItems();
            if (map.size() > 1) {
                map.sort((o1, o2) -> o2.get("timestamp").getS().compareTo(o1.get("timestamp").getS()));
                Double previousDayBalance = Double.parseDouble(map.get(1).get("currentBalance").getN());
                balanceRest.setPreviousDayValue(previousDayBalance);
            }
            Double currentDayBalance = Double.parseDouble(map.get(0).get("currentBalance").getN());
            currentUnit = currentDayBalance;
            balanceRest.setCurrentDayValue(currentDayBalance);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = accountTransactionService.repository.getBySecondaryField("accountId", id);
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            accountTransactionService.repository.delete(new AccountTransactionEntity(attributeValueMap.get("id").getS(), "", "", 0.0, false));
        }
        return super.delete(apiGatewayRequest, context, entityType);
    }

    @Override
    public AccountBrokerEntity convertRequestToEntity(AccountBrokerRest data) {
        return new AccountBrokerEntity(data.getId(), data.getAccountId(), data.getSymbol(), data.getName());
    }
}
