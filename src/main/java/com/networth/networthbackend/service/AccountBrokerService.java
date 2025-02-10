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
import com.networth.networthbackend.model.BalanceDetailRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AccountBrokerService extends Service<AccountBrokerRest, AccountBrokerEntity> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        ScanResult scanResult = repository.getBySecondaryField("accountId", id);
        List<AccountBrokerRest> accountBrokerRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            AccountBrokerRest accountRestResponse = new AccountBrokerRest(attributeValueMap.get("id").getS(), attributeValueMap.get("accountId").getS(), attributeValueMap.get("symbol").getS(), attributeValueMap.get("name").getS(), Double.parseDouble(attributeValueMap.get("currentUnit").getN()), getAccountCurrentAndPreviousDayBalance(attributeValueMap.get("id").getS()));
            accountBrokerRestList.add(accountRestResponse);
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountBrokerRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    private BalanceRest getAccountCurrentAndPreviousDayBalance(String accountId) {
        BalanceRest balanceRest = new BalanceRest();
        AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountId, AccountBalanceEntity.class);
        if (Objects.nonNull(accountBalanceEntity)) {
            balanceRest.setCurrentDayValue(new BalanceDetailRest(accountBalanceEntity.getBalance().getCurrentDayValue().getDateTime(), accountBalanceEntity.getBalance().getCurrentDayValue().getValue()));
            balanceRest.setPreviousDayValue(new BalanceDetailRest(accountBalanceEntity.getBalance().getPreviousDayValue().getDateTime(), accountBalanceEntity.getBalance().getPreviousDayValue().getValue()));
        } else {
            balanceRest.setCurrentDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
            balanceRest.setPreviousDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        }
        return balanceRest;
    }

    @Override
    public AccountBrokerEntity convertRequestToEntity(AccountBrokerRest data) {
        return new AccountBrokerEntity(data.getId(), data.getAccountId(), data.getSymbol(), data.getName(), data.getCurrentUnit());
    }
}
