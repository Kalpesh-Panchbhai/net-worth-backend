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
        } else if (apiGatewayRequest.getPath().startsWith("/account")) {
            return handleAccountRequest(apiGatewayRequest, context);
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
                return userService.save(apiGatewayRequest, context, User.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleAccountRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        AccountService accountService = new AccountService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return accountService.save(apiGatewayRequest, context, Account.class);
            case "GET":
                return accountService.getAllByUserID(apiGatewayRequest, context, Account.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeService incomeService = new IncomeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeService.save(apiGatewayRequest, context, Income.class);
            case "GET":
                return incomeService.getAllByUserID(apiGatewayRequest, context, Income.class);
            case "PATCH":
                return incomeService.update(apiGatewayRequest, context, Income.class);
            case "DELETE":
                return incomeService.delete(apiGatewayRequest, context, Income.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }

    public APIGatewayProxyResponseEvent handleIncomeTypeRequest(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        IncomeTypeService incomeTypeService = new IncomeTypeService();
        switch (apiGatewayRequest.getHttpMethod()) {
            case "POST":
                return incomeTypeService.save(apiGatewayRequest, context, IncomeType.class);
            case "GET":
                return incomeTypeService.getAllByUserID(apiGatewayRequest, context, IncomeType.class);
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
                return incomeTagService.save(apiGatewayRequest, context, IncomeTag.class);
            case "GET":
                return incomeTagService.getAllByUserID(apiGatewayRequest, context, IncomeTag.class);
            case "PATCH":
                return incomeTagService.update(apiGatewayRequest, context, IncomeTag.class);
            default:
                throw new Error("Unsupported Methods:::" + apiGatewayRequest.getHttpMethod());
        }
    }
}
