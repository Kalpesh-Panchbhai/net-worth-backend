package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.model.AccountBrokerRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.utility.Utility;

import java.util.*;

public class AccountBrokerService extends Service<AccountBrokerRest, AccountBrokerEntity> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final BalanceRest balanceRest = new BalanceRest(0.0, 0.0);

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("accountId", id);
        List<AccountBrokerRest> accountBrokerRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountBrokerRest accountRestResponse = new AccountBrokerRest(
                    attributeValueMap.get("id").getS(),
                    attributeValueMap.get("accountId").getS(),
                    attributeValueMap.get("symbol").getS(),
                    attributeValueMap.get("name").getS(),
                    Double.parseDouble(attributeValueMap.get("currentUnit").getN()),
                    getAccountCurrentAndPreviousDayBalance(attributeValueMap.get("id").getS()));
            accountBrokerRestList.add(accountRestResponse);
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountBrokerRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        AccountBrokerEntity accountBrokerEntity = repository.getByPrimaryField(id, AccountBrokerEntity.class);
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = super.delete(apiGatewayRequest, context, entityType);
        deleteAccountBalance(id, accountBrokerEntity, AccountBalanceEntity.class);
        return apiGatewayProxyResponseEvent;
    }

    public void deleteAccountBalance(String id, AccountBrokerEntity accountBrokerEntity, Class<AccountBalanceEntity> entityType) {
        AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(id, entityType);
        if (Objects.nonNull(accountBalanceEntity)) {
            accountBalanceRepository.delete(accountBalanceEntity);
        }
        ScanResult scanResult = repository.getBySecondaryField("accountId", accountBrokerEntity.getAccountId());
        Map<String, Double> accountBalance = new HashMap<>();
        accountBalance.put("currentDayValue", 0.0);
        accountBalance.put("previousDayValue", 0.0);
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountBalanceEntity accountBalanceEntity1 = accountBalanceRepository.getByPrimaryField(attributeValueMap.get("id").getS(), AccountBalanceEntity.class);
            if (Objects.nonNull(accountBalanceEntity1)) {
                accountBalance.put("currentDayValue", accountBalance.getOrDefault("currentDayValue", 0.0) + accountBalanceEntity1.getBalance().get("currentDayValue"));
                accountBalance.put("previousDayValue", accountBalance.getOrDefault("previousDayValue", 0.0) + accountBalanceEntity1.getBalance().get("previousDayValue"));
            }
        }
        accountBalanceRepository.save(new AccountBalanceEntity(accountBrokerEntity.getAccountId(), accountBalance));
    }

    private BalanceRest getAccountCurrentAndPreviousDayBalance(String accountId) {
        BalanceRest balanceRest = new BalanceRest(0.0, 0.0);
        AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountId, AccountBalanceEntity.class);
        if (Objects.nonNull(accountBalanceEntity)) {
            balanceRest.setCurrentDayValue(accountBalanceEntity.getBalance().get("currentDayValue"));
            balanceRest.setPreviousDayValue(accountBalanceEntity.getBalance().get("previousDayValue"));
        }
        return balanceRest;
    }

    @Override
    public AccountBrokerEntity convertRequestToEntity(AccountBrokerRest data) {
        return new AccountBrokerEntity(data.getId(), data.getAccountId(), data.getSymbol(), data.getName(), data.getCurrentUnit());
    }
}
