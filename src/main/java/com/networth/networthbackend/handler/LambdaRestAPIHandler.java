package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.*;
import com.networth.networthbackend.service.*;
import lombok.SneakyThrows;

public class LambdaRestAPIHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @SneakyThrows
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
        } else if (apiGatewayRequest.getPath().startsWith("/watch") && !apiGatewayRequest.getPath().contains("_")) {
            return handleWatchRequest(apiGatewayRequest, context);
        } else if (apiGatewayRequest.getPath().startsWith("/watch_account")) {
            return handleWatchAccountRequest(apiGatewayRequest, context);
        } else {
            throw new Error("Unsupported Path:::" + apiGatewayRequest.getPath());
        }
    }

    public APIGatewayProxyResponseEvent handleUserRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        UserService userService = new UserService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return userService.post(apiGatewayRequest, context, UserEntity.class);
                case "DELETE":
                    return userService.delete(apiGatewayRequest, context, UserEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            userService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleAccountRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountService accountService = new AccountService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return accountService.post(apiGatewayRequest, context, AccountEntity.class);
                case "GET":
                    return accountService.getBySecondaryField(apiGatewayRequest, context, AccountEntity.class);
                case "DELETE":
                    return accountService.delete(apiGatewayRequest, context, AccountEntity.class);
                case "PATCH":
                    return accountService.patch(apiGatewayRequest, context, AccountEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            accountService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleAccountBrokerRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return accountBrokerService.post(apiGatewayRequest, context, AccountBrokerEntity.class);
                case "GET":
                    return accountBrokerService.getBySecondaryField(apiGatewayRequest, context, AccountBrokerEntity.class);
                case "DELETE":
                    return accountBrokerService.delete(apiGatewayRequest, context, AccountBrokerEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            accountBrokerService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleAccountTransactionRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return accountTransactionService.post(apiGatewayRequest, context, AccountTransactionEntity.class);
                case "GET":
                    return accountTransactionService.getBySecondaryField(apiGatewayRequest, context, AccountTransactionEntity.class);
                case "DELETE":
                    return accountTransactionService.delete(apiGatewayRequest, context, AccountTransactionEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            accountTransactionService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeService incomeService = new IncomeService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return incomeService.post(apiGatewayRequest, context, IncomeEntity.class);
                case "GET":
                    return incomeService.getBySecondaryField(apiGatewayRequest, context, IncomeEntity.class);
                case "PATCH":
                    return incomeService.patch(apiGatewayRequest, context, IncomeEntity.class);
                case "DELETE":
                    return incomeService.delete(apiGatewayRequest, context, IncomeEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            incomeService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTypeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTypeService incomeTypeService = new IncomeTypeService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return incomeTypeService.post(apiGatewayRequest, context, IncomeTypeEntity.class);
                case "GET":
                    return incomeTypeService.getBySecondaryField(apiGatewayRequest, context, IncomeTypeEntity.class);
                case "PATCH":
                    return incomeTypeService.patch(apiGatewayRequest, context, IncomeTypeEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            incomeTypeService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTagRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTagService incomeTagService = new IncomeTagService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return incomeTagService.post(apiGatewayRequest, context, IncomeTagEntity.class);
                case "GET":
                    return incomeTagService.getBySecondaryField(apiGatewayRequest, context, IncomeTagEntity.class);
                case "PATCH":
                    return incomeTagService.patch(apiGatewayRequest, context, IncomeTagEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            incomeTagService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleWatchRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        WatchService watchService = new WatchService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return watchService.post(apiGatewayRequest, context, WatchEntity.class);
                case "GET":
                    return watchService.getBySecondaryField(apiGatewayRequest, context, WatchEntity.class);
                case "PATCH":
                    return watchService.patch(apiGatewayRequest, context, WatchEntity.class);
                case "DELETE":
                    return watchService.delete(apiGatewayRequest, context, WatchEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            watchService.repository.databaseService.disconnect();
        }
    }

    public APIGatewayProxyResponseEvent handleWatchAccountRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        WatchAccountService watchAccountService = new WatchAccountService();
        try {
            switch (apiGatewayRequest.getHttpMethod()) {
                case "POST":
                    return watchAccountService.post(apiGatewayRequest, context, WatchAccountEntity.class);
                case "GET":
                    return watchAccountService.getBySecondaryField(apiGatewayRequest, context, WatchAccountEntity.class);
                case "DELETE":
                    return watchAccountService.delete(apiGatewayRequest, context, WatchAccountEntity.class);
                default:
                    throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
            }
        } finally {
            watchAccountService.repository.databaseService.disconnect();
        }
    }
}
