package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.AccountTransactionRepository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.model.AccountTransactionRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.*;

public class AccountTransactionService extends Service<AccountTransactionRest, AccountTransactionEntity> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();

    public AccountTransactionService() {
        super(new AccountTransactionRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        AccountTransactionRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        updateCurrentBalance(requestData.getIsAccountTransaction(), requestData);
        AccountTransactionEntity savedData = convertRequestToEntity(requestData);
        repository.save(savedData);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    private void updateCurrentBalance(Boolean isAccountTransaction, AccountTransactionRest accountTransactionRest) {
        if (isAccountTransaction) {
            AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountTransactionRest.getAccountId(), AccountBalanceEntity.class);
            if (Objects.nonNull(accountBalanceEntity)) {
                Map<String, Double> accountBalance = accountBalanceEntity.getBalance();
                if (accountTransactionRest.getOperation().equals("UPDATE")) {
                    accountBalance.put("previousDayValue", accountBalance.get("currentDayValue"));
                    accountBalance.put("currentDayValue", accountTransactionRest.getAmount());
                    accountBalanceEntity.setBalance(accountBalance);
                    accountTransactionRest.setAmount(accountBalance.get("currentDayValue"));
                } else {
                    ScanResult scanResult = repository.getBySecondaryField("accountId", accountTransactionRest.getAccountId());
                    List<AccountTransactionRest> accountTransactionRestList = new ArrayList<>();
                    for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
                        AccountTransactionRest accountTransactionRestRequest = new AccountTransactionRest(attributeValueMap.get("id").getS(), attributeValueMap.get("accountId").getS(), attributeValueMap.get("timestamp").getS(), Double.parseDouble(attributeValueMap.get("currentBalance").getN()), attributeValueMap.get("paid").getBOOL(), "", false);
                        accountTransactionRestList.add(accountTransactionRestRequest);
                    }
                    accountTransactionRestList.sort(Comparator.comparing(AccountTransactionRest::getTimestamp).reversed());
                    accountBalance.put("previousDayValue", accountBalance.get("currentDayValue"));
                    accountBalance.put("currentDayValue", accountTransactionRestList.get(0).getAmount() + accountTransactionRest.getAmount());
                    accountBalanceEntity.setBalance(accountBalance);
                    accountTransactionRest.setAmount(accountBalance.get("currentDayValue"));
                }
                accountBalanceRepository.save(accountBalanceEntity);
            } else {
                AccountBalanceEntity accountBalanceEntityNew = new AccountBalanceEntity();
                accountBalanceEntityNew.setId(accountTransactionRest.getAccountId());
                accountBalanceEntityNew.setBalance(Map.of("previousDayValue", 0.0, "currentDayValue", accountTransactionRest.getAmount()));
                accountBalanceRepository.save(accountBalanceEntityNew);
            }
        } else {
            AccountBrokerService accountBrokerService = new AccountBrokerService();
            AccountBrokerEntity accountBrokerEntity = accountBrokerService.repository.getByPrimaryField(accountTransactionRest.getAccountId(), AccountBrokerEntity.class);
            if (Objects.nonNull(accountBrokerEntity)) {
                if (accountTransactionRest.getOperation().equals("UPDATE")) {
                    accountBrokerEntity.setCurrentUnit(accountTransactionRest.getAmount());
                } else {
                    accountBrokerEntity.setCurrentUnit(accountBrokerEntity.getCurrentUnit() + accountTransactionRest.getAmount());
                    accountTransactionRest.setAmount(accountBrokerEntity.getCurrentUnit());
                }
                accountBrokerService.repository.save(accountBrokerEntity);
            }
        }
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("accountId", id);
        List<AccountTransactionRest> accountTransactionRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountTransactionRest accountTransactionRestRequest = new AccountTransactionRest(attributeValueMap.get("id").getS(), attributeValueMap.get("accountId").getS(), attributeValueMap.get("timestamp").getS(), Double.parseDouble(attributeValueMap.get("currentBalance").getN()), attributeValueMap.get("paid").getBOOL(), "", false);
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