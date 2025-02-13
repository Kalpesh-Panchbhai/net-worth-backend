package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.WatchAccountEntity;
import com.networth.networthbackend.service.WatchAccountService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class NetWorthApplication {

    public static void main(String[] args) throws SQLException {
        WatchAccountService watchAccountService = new WatchAccountService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
//            apiGatewayProxyRequestEvent.setBody("{\n" + "    \"userId\": \"U1\"\n" + "}");
//            userService.post(apiGatewayProxyRequestEvent, getContext(), UserEntity.class);


            Map<String, String> headers = new HashMap<>();
            headers.put("id", "1");
            headers.put("type", "account");
            apiGatewayProxyRequestEvent.setPathParameters(headers);
            watchAccountService.getBySecondaryField(apiGatewayProxyRequestEvent, getContext(), WatchAccountEntity.class);
        } finally {
            watchAccountService.repository.databaseService.disconnect();
        }
    }

    private static Context getContext() {
        return new Context() {
            @Override
            public String getAwsRequestId() {
                return "";
            }

            @Override
            public String getLogGroupName() {
                return "";
            }

            @Override
            public String getLogStreamName() {
                return "";
            }

            @Override
            public String getFunctionName() {
                return "";
            }

            @Override
            public String getFunctionVersion() {
                return "";
            }

            @Override
            public String getInvokedFunctionArn() {
                return "";
            }

            @Override
            public CognitoIdentity getIdentity() {
                return null;
            }

            @Override
            public ClientContext getClientContext() {
                return null;
            }

            @Override
            public int getRemainingTimeInMillis() {
                return 0;
            }

            @Override
            public int getMemoryLimitInMB() {
                return 0;
            }

            @Override
            public LambdaLogger getLogger() {
                return new LambdaLogger() {
                    @Override
                    public void log(String s) {
                        System.out.println(s);
                    }
                };
            }
        };
    }
}
