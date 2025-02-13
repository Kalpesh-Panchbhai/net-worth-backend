package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.service.WatchService;

import java.util.HashMap;
import java.util.Map;

public class NetWorthApplication {

    public static void main(String[] args) {
        WatchService watchService = new WatchService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
//            apiGatewayProxyRequestEvent.setBody("{\n" +
//                    "    \"id\": 1,\n" +
//                    "    \"userId\": 1,\n" +
//                    "    \"amount\": 1000,\n" +
//                    "    \"taxPaid\": 10111,\n" +
//                    "    \"creditedDate\": \"2020-01-01\",\n" +
//                    "    \"currency\": \"IN111R\",\n" +
//                    "    \"incomeType\": 6,\n" +
//                    "    \"incomeTag\": 1\n" +
//                    "}");
//            incomeService.patch(apiGatewayProxyRequestEvent, getContext(), IncomeEntity.class);


            Map<String, String> headers = new HashMap<>();
            headers.put("id", "1");
            apiGatewayProxyRequestEvent.setPathParameters(headers);
            watchService.getBySecondaryField(apiGatewayProxyRequestEvent, getContext(), WatchEntity.class);
        } finally {
            watchService.repository.databaseService.disconnect();
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
