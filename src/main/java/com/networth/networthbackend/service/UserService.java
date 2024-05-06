package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.*;
import com.networth.networthbackend.utility.Utility;

import java.util.List;
import java.util.Map;

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
        List<AccountRest> accountRestList = accountService.getAllAccount(userId);
        for (AccountRest accountRest : accountRestList) {
            accountService.deleteOneAccount(accountRest.getId(), AccountEntity.class);
            accountService.repository.delete(accountService.convertRequestToEntity(accountRest));
        }
        List<WatchRest> watchRestList = watchService.getWatchList(userId);
        for (WatchRest watchRest : watchRestList) {
            watchService.repository.delete(watchService.convertRequestToEntity(watchRest));
        }
        List<IncomeRest> incomeRestList = incomeService.getIncomeList(userId);
        for (IncomeRest incomeRest : incomeRestList) {
            incomeService.repository.delete(incomeService.convertRequestToEntity(incomeRest));
        }
        List<TypeRest> incomeTagList = incomeTagService.getIncomeTagList(userId);
        for (TypeRest typeRest : incomeTagList) {
            incomeTagService.repository.delete(incomeTagService.convertRequestToEntity(typeRest));
        }
        List<TypeRest> incomeTypeList = incomeTypeService.getIncomeTypeList(userId);
        for (TypeRest typeRest : incomeTypeList) {
            incomeTypeService.repository.delete(incomeTypeService.convertRequestToEntity(typeRest));
        }
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = getBySecondaryField(apiGatewayRequest, context, type);
        if (isUserAvailable) {
            return apiGatewayProxyResponseEvent;
        }
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) {
        UserRest userEntity = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        ScanResult scanResult = repository.getBySecondaryField("userId", userEntity.getUserId());
        if (scanResult.getCount() > 0) {
            isUserAvailable = true;
        }
        UserRest userRest = new UserRest();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            userRest.setId(attributeValueMap.get("id").getS());
            userRest.setUserId(attributeValueMap.get("userId").getS());
        }
        String jsonBody = new Utility<UserRest>().convertObjToString(userRest, context);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    @Override
    public UserEntity convertRequestToEntity(UserRest userRest) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userRest.getId());
        userEntity.setUserId(userRest.getUserId());
        return userEntity;
    }
}