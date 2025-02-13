package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountTransactionRepository;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.Comparator;
import java.util.List;

public class AccountTransactionService extends Service<AccountTransactionEntity> {

    public AccountTransactionService() {
        super(new AccountTransactionRepository());
        secondaryFieldNameList = List.of("account_id", "account_broker_id");
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionEntity> entityType) {
        AccountTransactionEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        if (requestData.getOperation().equalsIgnoreCase("update")) {
            return super.post(apiGatewayRequest, context, entityType);
        } else {
            List<AccountTransactionEntity> accountTransactionEntityList;
            if (requestData.getAccountId() != null && requestData.getAccountId() != 0) {
                accountTransactionEntityList = repository.getBySecondaryField(List.of("account_id"), requestData.getAccountId());
            } else {
                accountTransactionEntityList = repository.getBySecondaryField(List.of("account_broker_id"), requestData.getAccountBrokerId());
            }
            accountTransactionEntityList.sort(Comparator.comparing(AccountTransactionEntity::getTransactionDate).reversed());
            Double lastValue = !accountTransactionEntityList.isEmpty() ? accountTransactionEntityList.get(0).getValue() : 0.0;
            requestData.setValue(requestData.getValue() + lastValue);
            Integer id = repository.save(requestData);
            RestResponse restResponse = Utility.generateRestResponse("success", id);
            String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
            context.getLogger().log("data added successfully to database:::" + jsonBody);
            return createAPIResponse(jsonBody, 201, Utility.createHeaders());
        }
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountTransactionEntity> entityType) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        String type = apiGatewayRequest.getPathParameters().get("type");
        List<AccountTransactionEntity> dataList = repository.getBySecondaryField(secondaryFieldNameList, id);
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}
