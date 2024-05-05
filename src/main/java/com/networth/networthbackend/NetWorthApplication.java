package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.service.AccountService;

import java.util.Map;

public class NetWorthApplication {

    public static void main(String[] args) {
        APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
        apiGatewayProxyRequestEvent.setPathParameters(Map.of("id", "c0c5cc88-1ac0-4703-9896-ce50464b56e9"));
//        apiGatewayProxyRequestEvent.setBody(getAccount());
        Context context = getContext();
        AccountService accountService = new AccountService();
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = accountService.delete(apiGatewayProxyRequestEvent, context, AccountEntity.class);
        System.out.println(apiGatewayProxyResponseEvent);
    }

    private static String getAccount() {
        return "{\n" + "    \"accountId\": \"c0c5cc88-1ac0-4703-9896-ce50464b56e9\",\n" + "    \"timestamp\": \"2024-05-02 20:00:00 UTC\",\n" + "    \"amount\": 100.0,\n" + "    \"paid\": true,\n" + "    \"operation\": \"UPDATE\"\n" + "}";
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

                    }
                };
            }
        };
    }
}
