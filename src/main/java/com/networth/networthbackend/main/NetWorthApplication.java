package com.networth.networthbackend.main;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.logging.LogLevel;
import com.networth.networthbackend.entity.InvestEntity;
import com.networth.networthbackend.service.ChartService;
import com.networth.networthbackend.service.InvestService;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NetWorthApplication {

    public static void main(String[] args) throws SQLException {
//        generateChartData(Boolean.valueOf(args[0]));
//        addInvestData();
    }

    private static void addInvestData() throws SQLException {
        InvestService investService = new InvestService();
        APIGatewayProxyRequestEvent apiGatewayRequestEvent = new APIGatewayProxyRequestEvent();
        InvestEntity investEntity = new InvestEntity();
        investEntity.setAccountId(17);
        investEntity.setOperation("update");
        investEntity.setDate("2025-07-14");
        investEntity.setValue(50000.0);
        apiGatewayRequestEvent.setBody(investEntity.toString());
        investService.post(apiGatewayRequestEvent, getContext(), InvestEntity.class);
        investService.repository.databaseService.disconnect();
    }

    private static void generateChartData(Boolean onlyDhan) throws SQLException {
        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        ChartService chartService = new ChartService(getContext());
        if (!onlyDhan) {
            chartService.generateData();
        } else {
            chartService.generateDhanNonBrokerAccountData(17);
        }
        chartService.repository.databaseService.disconnect();
        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    public static Context getContext() {
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

                    @Override
                    public void log(byte[] bytes) {

                    }

                    @Override
                    public void log(String message, LogLevel logLevel) {
                        LambdaLogger.super.log(message, logLevel);
                    }
                };
            }
        };
    }
}