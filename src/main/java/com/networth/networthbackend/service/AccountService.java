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
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.AccountRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AccountService extends Service<AccountRest, AccountEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountTransactionService accountTransactionService = new AccountTransactionService();
    private final WatchService watchService = new WatchService();

    public AccountService() {
        super(new AccountRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        AccountRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        AccountEntity savedData = convertRequestToEntity(requestData);
        repository.save(savedData);
        String accountId = savedData.getId();
        String userId = savedData.getUserId();
        addAccountInDefaultWatch(userId, accountId);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    private void addAccountInDefaultWatch(String userId, String accountId) {
        ScanResult scanResult = watchService.repository.getBySecondaryField("userId", userId);
        String watchAllId = scanResult.getItems().stream().filter((data) -> data.get("name").getS().equals("All")).collect(Collectors.toList()).get(0).get("id").getS();

        WatchEntity watchEntity = watchService.repository.getByPrimaryField(watchAllId, WatchEntity.class);
        watchEntity.getAccountList().add(accountId);
        watchService.repository.update(watchEntity);
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<AccountRest> accountRestList = getAllAccount(id);
        String jsonBody = utilityRest.convertListOfObjToString(accountRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<AccountRest> getAllAccount(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        List<AccountRest> accountRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountRest accountRestResponse = new AccountRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("accountName").getS(), attributeValueMap.get("accountType").getS(), attributeValueMap.containsKey("loanType") ? attributeValueMap.get("loanType").getS() : "", attributeValueMap.get("currency").getS(), attributeValueMap.get("active").getBOOL(), getAccountCurrentAndPreviousDayBalance(attributeValueMap.get("id").getS()));
            accountRestList.add(accountRestResponse);
        }
        return accountRestList;
    }

    private BalanceRest getAccountCurrentAndPreviousDayBalance(String accountId) {
        BalanceRest balanceRest = new BalanceRest(0.0, 0.0);
        ScanResult scanResult = accountTransactionService.repository.getBySecondaryField("accountId", accountId);
        if (scanResult.getCount() > 0) {
            List<Map<String, AttributeValue>> map = scanResult.getItems();
            if (map.size() > 1) {
                map.sort((o1, o2) -> o2.get("timestamp").getS().compareTo(o1.get("timestamp").getS()));
                Double previousDayBalance = Double.parseDouble(map.get(1).get("currentBalance").getN());
                balanceRest.setPreviousDayValue(previousDayBalance);
            }
            Double currentDayBalance = Double.parseDouble(map.get(0).get("currentBalance").getN());
            balanceRest.setCurrentDayValue(currentDayBalance);
        }
        return balanceRest;
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        deleteOneAccount(id, entityType);
        return super.delete(apiGatewayRequest, context, entityType);
    }

    public void deleteOneAccount(String id, Class<AccountEntity> entityType) {
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
