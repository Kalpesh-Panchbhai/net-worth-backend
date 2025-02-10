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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AccountService extends Service<AccountRest, AccountEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final WatchService watchService = new WatchService();
    private final DatabaseService databaseService = new DatabaseService();
    private final Connection connection;

    public AccountService() {
        super(new AccountRepository());
        connection = databaseService.getConnection();
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        AccountRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        try {
            String insertQuery = "INSERT into accounts (name, type, active, currency, user_id) values(?, ? , ? , ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(insertQuery);
            pstmt.setString(1, requestData.getName());
            pstmt.setString(2, requestData.getType());
            pstmt.setBoolean(3, requestData.getActive());
            pstmt.setString(4, requestData.getCurrency());
            pstmt.setString(5, requestData.getUserId());

            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("Database error");
            System.err.println(e.getMessage());
        } finally {
            databaseService.disconnect();
        }
//        addAccountInDefaultWatch(userId, accountId);
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


    public APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        String userId = apiGatewayRequest.getPathParameters().get("id");
        List<AccountRest> accountList = new ArrayList<>();
        try {
            String query = "select * from accounts where user_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String type = rs.getString("type");
                Boolean active = rs.getBoolean("active");
                String currency = rs.getString("currency");
                accountList.add(new AccountRest(id, userId, name, type, currency, active));
            }
        } catch (Exception e) {
            System.err.println("Database error");
            System.err.println(e.getMessage());
        } finally {
            databaseService.disconnect();
        }
        String jsonBody = utilityRest.convertListOfObjToString(accountList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
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

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        return null;
    }

    @Override
    public APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) throws SQLException {
        return null;
    }

    @Override
    public AccountEntity convertRequestToEntity(AccountRest data) {
        return null;
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

}
