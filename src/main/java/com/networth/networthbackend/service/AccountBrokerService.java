package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.utility.Utility;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

public class AccountBrokerService extends Service<AccountBrokerEntity> {

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountBrokerEntity> entityType) throws SQLException {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<AccountBrokerEntity> dataList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_id", id);
        }});
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}
