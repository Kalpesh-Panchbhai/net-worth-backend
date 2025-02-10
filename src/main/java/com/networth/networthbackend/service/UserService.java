package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.UserRest;
import com.networth.networthbackend.utility.Utility;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserService extends Service<UserRest, UserEntity> {

    private final AccountService accountService = new AccountService();
    private final WatchService watchService = new WatchService();
    private final IncomeService incomeService = new IncomeService();
    private final IncomeTagService incomeTagService = new IncomeTagService();
    private final IncomeTypeService incomeTypeService = new IncomeTypeService();
    private Boolean isUserAvailable = false;

    public UserService() {
        super(new UserRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        deleteUser(id);
        return super.delete(apiGatewayRequest, context, entityType);
    }

    private void deleteUser(String userId) {
//        List<AccountRest> accountRestList = accountService.getAllAccount(userId);
//        for (AccountRest accountRest : accountRestList) {
//            accountService.deleteOneAccount(accountRest.getId(), AccountEntity.class);
//            accountService.repository.delete(accountService.convertRequestToEntity(accountRest));
//        }
//        List<WatchRest> watchRestList = watchService.getWatchList(userId);
//        for (WatchRest watchRest : watchRestList) {
//            watchService.repository.delete(watchService.convertRequestToEntity(watchRest));
//        }
//        List<IncomeRest> incomeRestList = incomeService.getIncomeList(userId);
//        for (IncomeRest incomeRest : incomeRestList) {
//            incomeService.repository.delete(incomeService.convertRequestToEntity(incomeRest));
//        }
//        List<TypeRest> incomeTagList = incomeTagService.getIncomeTagList(userId);
//        for (TypeRest typeRest : incomeTagList) {
//            incomeTagService.repository.delete(incomeTagService.convertRequestToEntity(typeRest));
//        }
//        List<TypeRest> incomeTypeList = incomeTypeService.getIncomeTypeList(userId);
//        for (TypeRest typeRest : incomeTypeList) {
//            incomeTypeService.repository.delete(incomeTypeService.convertRequestToEntity(typeRest));
//        }
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) throws SQLException {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = getByPrimaryField(apiGatewayRequest, context, type);
        if (isUserAvailable) {
            return apiGatewayProxyResponseEvent;
        }
//        UserRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
//        UserEntity savedData = convertRequestToEntity(requestData);
//        repository.save(savedData);
//        watchService.repository.save(new WatchEntity(null, savedData.getId(), "All", new ArrayList<>()));
//        requestData.setId(savedData.getId());
//        String jsonBody = utilityRest.convertObjToString(requestData, context);
        String jsonBody = "";
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) {
        UserRest userEntity = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
//        ScanResult scanResult = repository.getBySecondaryField("userId", userEntity.getUserId());
//        if (scanResult.getCount() > 0) {
//            isUserAvailable = true;
//        }
        UserRest userRest = new UserRest();
        String jsonBody = new Utility<UserRest>().convertObjToString(userRest, context);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public APIGatewayProxyResponseEvent getByPrimaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) throws SQLException {
        UserRest userEntity = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        ResultSet resultSet = repository.getByPrimaryField(userEntity.getId());
        UserRest userRest = new UserRest();
        try {
            if (resultSet.next()) {
                isUserAvailable = true;
                userRest.setId(resultSet.getString("id"));
            }
        } catch (Exception e) {

        } finally {
            repository.databaseService.disconnect();
        }
        String jsonBody = new Utility<UserRest>().convertObjToString(userRest, context);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public UserEntity convertRequestToEntity(UserRest userRest) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userRest.getId());
        return userEntity;
    }
}