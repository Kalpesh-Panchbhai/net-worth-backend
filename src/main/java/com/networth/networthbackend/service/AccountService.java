package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.model.AccountRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountService extends Service<AccountRest, AccountEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountTransactionService accountTransactionService = new AccountTransactionService();

    public AccountService() {
        super(new AccountRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("userId", id);
        List<AccountRest> accountRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountRest accountRestResponse = new AccountRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("accountName").getS(), attributeValueMap.get("accountType").getS(), attributeValueMap.containsKey("loanType") ? attributeValueMap.get("loanType").getS() : "", attributeValueMap.get("currency").getS(), attributeValueMap.get("active").getBOOL(), new BalanceRest());
            accountRestList.add(accountRestResponse);
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        AccountEntity accountEntity = repository.getByPrimaryField(id, entityType);
        if (accountEntity.getAccountType().equals("Broker")) {
            ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", id);
            for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
                ScanResult scanResult1 = accountTransactionService.repository.getBySecondaryField("accountId", attributeValueMap.get("id").getS());
                for (Map<String, AttributeValue> attributeValueMap1 : scanResult1.getItems()) {
                    accountTransactionService.repository.delete(new AccountTransactionEntity(attributeValueMap1.get("id").getS(), "", "", 0.0, false));
                }
                accountBrokerService.repository.delete(new AccountBrokerEntity(attributeValueMap.get("id").getS(), "", "", ""));
            }
        } else {
            ScanResult scanResult = accountTransactionService.repository.getBySecondaryField("accountId", id);
            for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
                accountTransactionService.repository.delete(new AccountTransactionEntity(attributeValueMap.get("id").getS(), "", "", 0.0, false));
            }
        }
        return super.delete(apiGatewayRequest, context, entityType);
    }

    @Override
    public AccountEntity convertRequestToEntity(AccountRest accountRestRequest) {
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setId(accountRestRequest.getId());
        accountEntity.setUserId(accountRestRequest.getUserId());
        accountEntity.setAccountName(accountRestRequest.getAccountName());
        accountEntity.setAccountType(accountRestRequest.getAccountType());
        accountEntity.setLoanType(accountRestRequest.getLoanType());
        accountEntity.setCurrency(accountRestRequest.getCurrency());
        accountEntity.setActive(accountRestRequest.getActive());
        return accountEntity;
    }
}
