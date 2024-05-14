package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.Repository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.model.AccountTransactionRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.model.yahooapimodel.ChartData;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class AccountTransactionService extends Service<AccountTransactionRest, Void> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountBalanceService accountBalanceService = new AccountBalanceService();

    public AccountTransactionService() {
        super(new Repository<>());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) throws IOException, URISyntaxException, InterruptedException {
        AccountTransactionRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        updateAccountBalance(requestData);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    private void updateAccountBalance(AccountTransactionRest requestData) throws IOException, URISyntaxException, InterruptedException {
        if (requestData.getIsAccountTransaction()) {
            AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(requestData.getAccountId(), AccountBalanceEntity.class);
            if (Objects.nonNull(accountBalanceEntity)) {
                if (requestData.getOperation().equals("UPDATE")) {
                    accountBalanceEntity.getBalance().put("previousDayValue", accountBalanceEntity.getBalance().get("currentDayValue"));
                    accountBalanceEntity.getBalance().put("currentDayValue", requestData.getAmount());
                } else {
                    accountBalanceEntity.getBalance().put("previousDayValue", accountBalanceEntity.getBalance().get("currentDayValue"));
                    accountBalanceEntity.getBalance().put("currentDayValue", accountBalanceEntity.getBalance().get("currentDayValue") + requestData.getAmount());
                }
            } else {
                accountBalanceEntity = new AccountBalanceEntity();
                accountBalanceEntity.setId(requestData.getAccountId());
                accountBalanceEntity.setBalance(Map.of("currentDayValue", requestData.getAmount(), "previousDayValue", 0.0));
            }
            accountBalanceRepository.save(accountBalanceEntity);
        } else {
            AccountBrokerEntity accountBrokerEntity = accountBrokerService.repository.getByPrimaryField(requestData.getAccountId(), AccountBrokerEntity.class);
            if (requestData.getOperation().equals("UPDATE")) {
                accountBrokerEntity.setCurrentUnit(requestData.getAmount());
            } else {
                accountBrokerEntity.setCurrentUnit(accountBrokerEntity.getCurrentUnit() + requestData.getAmount());
            }
            updateAccountBrokerBalance(accountBrokerEntity);
            accountBrokerService.repository.save(accountBrokerEntity);
        }
    }

    private void updateAccountBrokerBalance(AccountBrokerEntity accountBrokerEntity) throws IOException, URISyntaxException, InterruptedException {
        List<ChartData> chartDataList = accountBalanceService.generateData(accountBrokerEntity.getSymbol());
        Map<String, Double> accountBalance = accountBalanceService.getAccountBalance(accountBrokerEntity, chartDataList);
        AccountBalanceEntity accountBalanceEntity = new AccountBalanceEntity();
        accountBalanceEntity.setBalance(accountBalance);
        accountBalanceEntity.setId(accountBrokerEntity.getId());
        accountBalanceService.getAccountBalanceRepository().save(accountBalanceEntity);
        ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", accountBrokerEntity.getAccountId());
        List<String> accountIdList = new ArrayList<>();
        for (Map<String, AttributeValue> data : scanResult.getItems()) {
            accountIdList.add(data.get("id").getS());
        }
        Map<String, Double> accountBrokerBalanceList = new HashMap<>();
        for (String accountId : accountIdList) {
            AccountBalanceEntity accountBalanceEntity1 = accountBalanceService.getAccountBalanceRepository().getByPrimaryField(accountId, AccountBalanceEntity.class);
            accountBrokerBalanceList.put("currentDayValue", accountBrokerBalanceList.getOrDefault("currentDayValue", 0.0) + accountBalanceEntity1.getBalance().get("currentDayValue"));
            accountBrokerBalanceList.put("previousDayValue", accountBrokerBalanceList.getOrDefault("previousDayValue", 0.0) + accountBalanceEntity1.getBalance().get("previousDayValue"));
        }
        accountBalanceEntity = new AccountBalanceEntity();
        accountBalanceEntity.setId(accountBrokerEntity.getAccountId());
        accountBalanceEntity.setBalance(accountBrokerBalanceList);
        accountBalanceService.getAccountBalanceRepository().save(accountBalanceEntity);
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        // NOT SUPPORTED
        return null;
    }

    @Override
    public Void convertRequestToEntity(AccountTransactionRest data) {
        return null;
    }
}
