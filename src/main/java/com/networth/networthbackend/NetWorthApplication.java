package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.ChartEntity;
import com.networth.networthbackend.service.ChartService;

public class NetWorthApplication {

    public static void main(String[] args) {
        ChartService chartService = new ChartService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            apiGatewayProxyRequestEvent.setBody("{\n" + "    \"accountBrokerId\": 3,\n" + "    \"date\": \"2024-02-02\",\n" + "    \"value\": 10\n" + "}");
            chartService.post(apiGatewayProxyRequestEvent, getContext(), ChartEntity.class);
        } finally {
            chartService.repository.databaseService.disconnect();
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
