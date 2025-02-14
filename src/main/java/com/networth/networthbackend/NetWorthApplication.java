package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.service.AccountBrokerService;

import java.sql.SQLException;

public class NetWorthApplication {

    public static void main(String[] args) throws SQLException {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        try {

            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            apiGatewayProxyRequestEvent.setBody("{\n" +
                    "      \"symbol\": \"0P0001DJZI.BO\",\n" +
                    "      \"name\": \"ICICI Pru BHARAT 22 FOF Dir Gr\",\n" +
                    "      \"accountId\": \"3\"\n" +
                    "    }");
            accountBrokerService.post(apiGatewayProxyRequestEvent, getContext(), AccountBrokerEntity.class);


//            Map<String, String> headers = new HashMap<>();
//            headers.put("id", "3");
//            apiGatewayProxyRequestEvent.setPathParameters(headers);
//            accountBrokerService.getBySecondaryField(apiGatewayProxyRequestEvent, getContext(), AccountBrokerEntity.class);
        } finally {
            accountBrokerService.repository.databaseService.disconnect();
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
