package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.entity.IncomeEntity;
import com.networth.networthbackend.model.*;
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
                return userService.post(apiGatewayRequest, context, UserRest.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountService accountService = new AccountService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountService.post(apiGatewayRequest, context, AccountRest.class);
            case "GET":
                return accountService.getBySecondaryField(apiGatewayRequest, context, AccountRest.class);
            case "DELETE":
                return accountService.delete(apiGatewayRequest, context, AccountEntity.class);
            case "PATCH":
                return accountService.patch(apiGatewayRequest, context, AccountRest.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountBrokerRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountBrokerService.post(apiGatewayRequest, context, AccountBrokerRest.class);
            case "GET":
                return accountBrokerService.getBySecondaryField(apiGatewayRequest, context, AccountBrokerRest.class);
            case "DELETE":
                return accountBrokerService.delete(apiGatewayRequest, context, AccountBrokerEntity.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountTransactionRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountTransactionService.post(apiGatewayRequest, context, AccountTransactionRest.class);
            case "GET":
                return accountTransactionService.getBySecondaryField(apiGatewayRequest, context, AccountTransactionRest.class);
            case "DELETE":
                return accountTransactionService.delete(apiGatewayRequest, context, AccountTransactionEntity.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeService incomeService = new IncomeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeService.post(apiGatewayRequest, context, IncomeRest.class);
            case "GET":
                return incomeService.getBySecondaryField(apiGatewayRequest, context, IncomeRest.class);
            case "PATCH":
                return incomeService.patch(apiGatewayRequest, context, IncomeRest.class);
            case "DELETE":
                return incomeService.delete(apiGatewayRequest, context, IncomeEntity.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTypeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTypeService incomeTypeService = new IncomeTypeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeTypeService.post(apiGatewayRequest, context, TypeRest.class);
            case "GET":
                return incomeTypeService.getBySecondaryField(apiGatewayRequest, context, TypeRest.class);
            case "PATCH":
                return incomeTypeService.patch(apiGatewayRequest, context, TypeRest.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTagRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTagService incomeTagService = new IncomeTagService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeTagService.post(apiGatewayRequest, context, TypeRest.class);
            case "GET":
                return incomeTagService.getBySecondaryField(apiGatewayRequest, context, TypeRest.class);
            case "PATCH":
                return incomeTagService.patch(apiGatewayRequest, context, TypeRest.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }
}
