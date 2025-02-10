package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.UserRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;

public class UserService extends Service<UserRest, UserEntity> {

    private final WatchService watchService = new WatchService();
    private final Boolean isUserAvailable = false;

    public UserService() {
        super(new UserRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserRest> type) {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = getBySecondaryField(apiGatewayRequest, context, UserEntity.class);
        if (isUserAvailable) {
            return apiGatewayProxyResponseEvent;
        }
        UserRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        UserEntity savedData = convertRequestToEntity(requestData);
        repository.save(savedData);
        watchService.repository.save(new WatchEntity(null, savedData.getId(), "All", new ArrayList<>()));
        requestData.setId(savedData.getId());
        String jsonBody = utilityRest.convertObjToString(requestData, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    @Override
    public UserEntity convertRequestToEntity(UserRest userRest) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userRest.getId());
        return userEntity;
    }
}