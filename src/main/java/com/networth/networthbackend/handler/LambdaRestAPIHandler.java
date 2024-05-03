package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.*;
import com.networth.networthbackend.service.*;

public class LambdaRestAPIHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        if (apiGatewayRequest.getPath().startsWith("/user")) {
            return handleUserRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/account") && !apiGatewayRequest.getPath().contains("_")) {
            return handleAccountRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/account_broker")) {
            return handleAccountBrokerRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/account_transaction")) {
            return handleAccountTransactionRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/income") && !apiGatewayRequest.getPath().contains("_")) {
            return handleIncomeRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/income_type")) {
            return handleIncomeTypeRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/income_tag")) {
            return handleIncomeTagRequest(apiGatewayRequest, context);
        } else {
            throw new Error("Unsupported Path:::" + apiGatewayRequest.getPath());
        }
    }

    public APIGatewayProxyResponseEvent handleUserRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        UserService userService = new UserService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return userService.add(apiGatewayRequest, context, User.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountService accountService = new AccountService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountService.add(apiGatewayRequest, context, Account.class);
            case "GET":
                return accountService.getAllByUniqueID(apiGatewayRequest, context, Account.class);
            case "DELETE":
                return accountService.delete(apiGatewayRequest, context);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountBrokerRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountBrokerService.add(apiGatewayRequest, context, AccountBroker.class);
            case "GET":
                return accountBrokerService.getAllByUniqueID(apiGatewayRequest, context, AccountBroker.class);
            case "DELETE":
                return accountBrokerService.delete(apiGatewayRequest, context);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountTransactionRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountTransactionService.add(apiGatewayRequest, context, AccountTransaction.class);
            case "GET":
                return accountTransactionService.getAllByUniqueID(apiGatewayRequest, context, AccountTransaction.class);
            case "DELETE":
                return accountTransactionService.delete(apiGatewayRequest, context);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeService incomeService = new IncomeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeService.add(apiGatewayRequest, context, Income.class);
            case "GET":
                return incomeService.getAllByUniqueID(apiGatewayRequest, context, Income.class);
            case "PATCH":
                return incomeService.update(apiGatewayRequest, context, Income.class);
            case "DELETE":
                return incomeService.delete(apiGatewayRequest, context);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTypeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTypeService incomeTypeService = new IncomeTypeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeTypeService.add(apiGatewayRequest, context, IncomeType.class);
            case "GET":
                return incomeTypeService.getAllByUniqueID(apiGatewayRequest, context, IncomeType.class);
            case "PATCH":
                return incomeTypeService.update(apiGatewayRequest, context, IncomeType.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTagRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTagService incomeTagService = new IncomeTagService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeTagService.add(apiGatewayRequest, context, IncomeTag.class);
            case "GET":
                return incomeTagService.getAllByUniqueID(apiGatewayRequest, context, IncomeTag.class);
            case "PATCH":
                return incomeTagService.update(apiGatewayRequest, context, IncomeTag.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }
}
