package com.networth.networthbackend;


import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networth.networthbackend.entity.IncomeTypeEntity;
import com.networth.networthbackend.service.IncomeTypeService;

public class NetWorthApplication {

    public static void main(String[] args) {
        IncomeTypeService incomeTypeService = new IncomeTypeService();
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            apiGatewayProxyRequestEvent.setBody("{\n" +
                    "    \"id\": 4,\n" +
                    "    \"userId\": 1,\n" +
                    "    \"name\": \"A\",\n" +
                    "    \"isDefault\": true\n" +
                    "}");
            incomeTypeService.patch(apiGatewayProxyRequestEvent, getContext(), IncomeTypeEntity.class);


//            Map<String, String> headers = new HashMap<>();
//            headers.put("id", "1");
//            headers.put("type", "account");
//            apiGatewayProxyRequestEvent.setPathParameters(headers);
//            accountTransactionService.getBySecondaryField(apiGatewayProxyRequestEvent, getContext(), AccountTransactionEntity.class);
        } finally {
            incomeTypeService.repository.databaseService.disconnect();
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
