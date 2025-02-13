package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.service.AccountTransactionService;

public class NetWorthApplication {

    public static void main(String[] args) {
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            apiGatewayProxyRequestEvent.setBody("{\n" + "    \"accountId\": \"1\",\n" + "    \"transactionDate\": \"2021-01-01 10:01:01\",\n" + "    \"value\": 10.00\n" + "}");
//            Map<String, String> headers = new HashMap<>();
//            headers.put("id", "1");
//            apiGatewayProxyRequestEvent.setPathParameters(headers);
            accountTransactionService.post(apiGatewayProxyRequestEvent, getContext(), AccountTransactionEntity.class);
        } finally {
            accountTransactionService.repository.databaseService.disconnect();
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
