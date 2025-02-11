package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.List;

public class UserService extends Service<UserEntity> {

    public UserService() {
        super(new UserRepository());
        secondaryFieldNameList = List.of("user_id");
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserEntity> entityType) {
        UserEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        List<UserEntity> userEntityList = repository.getBySecondaryField(secondaryFieldNameList, requestData.getUserId());
        if (userEntityList.isEmpty()) {
            return super.post(apiGatewayRequest, context, entityType);
        }
        RestResponse restResponse = new RestResponse(String.valueOf(userEntityList.get(0).getId()));
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}