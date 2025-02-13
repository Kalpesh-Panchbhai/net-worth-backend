package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.LinkedHashMap;
import java.util.List;

public class UserService extends Service<UserEntity> {

    public UserService() {
        super(new UserRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<UserEntity> entityType) {
        UserEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        List<UserEntity> userEntityList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", requestData.getUserId());
        }});
        if (userEntityList.isEmpty()) {
            APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = super.post(apiGatewayRequest, context, entityType);
            WatchService watchService = new WatchService();
            RestResponse restResponse = new Utility<RestResponse>().convertStringToObj(apiGatewayProxyResponseEvent.getBody(), context, RestResponse.class);
            WatchEntity watchEntity = new WatchEntity();
            watchEntity.setUserId(restResponse.getId());
            watchEntity.setName("All");
            watchService.repository.save(watchEntity);
            return apiGatewayProxyResponseEvent;
        }
        RestResponse restResponse = Utility.generateRestResponse("success", userEntityList.get(0).getId());
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}