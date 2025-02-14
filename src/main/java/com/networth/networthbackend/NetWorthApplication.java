package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.service.AccountService;

import java.sql.SQLException;

public class NetWorthApplication {

    public static void main(String[] args) throws SQLException {
        AccountService accountService = new AccountService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            apiGatewayProxyRequestEvent.setBody("{\n" + "    \"userId\": 1,\n" + "    \"name\": \"B\",\n" + "    \"type\": \"Non Broker\",\n" + "    \"currency\": \"INR\",\n" + "    \"active\": true\n" + "}");
            accountService.post(apiGatewayProxyRequestEvent, getContext(), AccountEntity.class);


//            Map<String, String> headers = new HashMap<>();
//            headers.put("id", "3");
//            apiGatewayProxyRequestEvent.setPathParameters(headers);
//            userService.getBySecondaryField(apiGatewayProxyRequestEvent, getContext(), UserEntity.class);
        } finally {
            accountService.repository.databaseService.disconnect();
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
