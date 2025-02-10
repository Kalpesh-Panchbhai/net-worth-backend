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
import com.networth.networthbackend.model.BalanceDetailRest;
import com.networth.networthbackend.model.BalanceRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.model.yahooapimodel.ChartData;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                    accountBalanceEntity.getBalance().getPreviousDayValue().setDateTime(accountBalanceEntity.getBalance().getCurrentDayValue().getDateTime());
                    accountBalanceEntity.getBalance().getPreviousDayValue().setValue(accountBalanceEntity.getBalance().getCurrentDayValue().getValue());
                    accountBalanceEntity.getBalance().getCurrentDayValue().setDateTime(Utility.convertLocalToUTC());
                    accountBalanceEntity.getBalance().getCurrentDayValue().setValue(requestData.getAmount());
                } else {
                    accountBalanceEntity.getBalance().getPreviousDayValue().setDateTime(accountBalanceEntity.getBalance().getCurrentDayValue().getDateTime());
                    accountBalanceEntity.getBalance().getPreviousDayValue().setValue(accountBalanceEntity.getBalance().getCurrentDayValue().getValue());
                    accountBalanceEntity.getBalance().getCurrentDayValue().setDateTime(Utility.convertLocalToUTC());
                    accountBalanceEntity.getBalance().getCurrentDayValue().setValue(accountBalanceEntity.getBalance().getCurrentDayValue().getValue() + requestData.getAmount());
                }
            } else {
                accountBalanceEntity = new AccountBalanceEntity();
                accountBalanceEntity.setId(requestData.getAccountId());
                accountBalanceEntity.setBalance(new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), requestData.getAmount()), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0)));
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
        BalanceRest balance = accountBalanceService.getAccountBalance(accountBrokerEntity, chartDataList);
        AccountBalanceEntity accountBalanceEntity = new AccountBalanceEntity();
        accountBalanceEntity.setBalance(balance);
        accountBalanceEntity.setId(accountBrokerEntity.getId());
        accountBalanceService.getAccountBalanceRepository().save(accountBalanceEntity);
        ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", accountBrokerEntity.getAccountId());
        List<String> accountIdList = new ArrayList<>();
        for (Map<String, AttributeValue> data : scanResult.getItems()) {
            accountIdList.add(data.get("id").getS());
        }
        BalanceRest balanceRest = new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        for (String accountId : accountIdList) {
            AccountBalanceEntity accountBalanceEntity1 = accountBalanceService.getAccountBalanceRepository().getByPrimaryField(accountId, AccountBalanceEntity.class);
            if (Objects.nonNull(accountBalanceEntity1)) {
                balanceRest.setCurrentDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), balanceRest.getCurrentDayValue().getValue() + accountBalanceEntity1.getBalance().getCurrentDayValue().getValue()));
                balanceRest.setPreviousDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), balanceRest.getPreviousDayValue().getValue() + accountBalanceEntity1.getBalance().getPreviousDayValue().getValue()));
            }
        }
        accountBalanceEntity = new AccountBalanceEntity();
        accountBalanceEntity.setId(accountBrokerEntity.getAccountId());
        accountBalanceEntity.setBalance(balanceRest);
        accountBalanceService.getAccountBalanceRepository().save(accountBalanceEntity);
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) {
        // NOT SUPPORTED
        return null;
    }

    @Override
    public APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionRest> type) throws SQLException {
        return null;
    }

    @Override
    public Void convertRequestToEntity(AccountTransactionRest data) {
        return null;
    }
}
