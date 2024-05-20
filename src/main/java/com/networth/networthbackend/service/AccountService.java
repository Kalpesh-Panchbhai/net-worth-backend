package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.AccountRest;
import com.networth.networthbackend.model.BalanceDetailRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AccountService extends Service<AccountRest, AccountEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
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
        BalanceRest balanceRest = new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountId, AccountBalanceEntity.class);
        if (Objects.nonNull(accountBalanceEntity)) {
            balanceRest.getCurrentDayValue().setDateTime(accountBalanceEntity.getBalance().getCurrentDayValue().getDateTime());
            balanceRest.getCurrentDayValue().setValue(accountBalanceEntity.getBalance().getCurrentDayValue().getValue());
            balanceRest.getPreviousDayValue().setDateTime(accountBalanceEntity.getBalance().getPreviousDayValue().getDateTime());
            balanceRest.getPreviousDayValue().setValue(accountBalanceEntity.getBalance().getPreviousDayValue().getValue());
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
        accountBalanceRepository.delete(new AccountBalanceEntity(id, null));
        if (Objects.nonNull(accountEntity) && Objects.nonNull(accountEntity.getAccountType()) && accountEntity.getAccountType().equals("Broker")) {
            ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", id);
            for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
                accountBrokerService.repository.delete(new AccountBrokerEntity(attributeValueMap.get("id").getS(), "", "", "", 0.0));
                accountBalanceRepository.delete(new AccountBalanceEntity(attributeValueMap.get("id").getS(), null));
            }
        }
        if (Objects.nonNull(accountEntity) && Objects.nonNull(accountEntity.getUserId())) {
            watchService.repository.getBySecondaryField("userId", accountEntity.getUserId()).getItems().forEach((data) -> {
                WatchEntity watchEntity = watchService.repository.getByPrimaryField(data.get("id").getS(), WatchEntity.class);
                if (watchEntity.getAccountList().contains(accountEntity.getId())) {
                    watchEntity.getAccountList().remove(accountEntity.getId());
                    watchService.repository.update(watchEntity);
                }
            });
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
