package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.service.UserService;

import java.net.URISyntaxException;
import java.util.Map;

public class NetWorthApplication {

    public static void main(String[] args) throws URISyntaxException, InterruptedException {
        UserService userService = new UserService();
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent.setPathParameters(Map.of("id", "639ec106-62f1-4442-82d0-2db25cab87cc"));
        userService.delete(apiGatewayProxyRequestEvent, null, UserEntity.class);
    }
}
