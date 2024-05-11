package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.Repository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.model.AccountTransactionRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.Map;
import java.util.Objects;

public class AccountTransactionService extends Service<AccountTransactionRest, Void> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final AccountBrokerService accountBrokerService = new AccountBrokerService();

    public AccountTransactionService() {
        super(new Repository<>());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        AccountTransactionRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        updateAccountBalance(requestData);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    private void updateAccountBalance(AccountTransactionRest requestData) {
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
            accountBrokerService.repository.save(accountBrokerEntity);
        }
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
