package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.WatchAccountRepository;
import com.networth.networthbackend.entity.WatchAccountEntity;
import com.networth.networthbackend.utility.Utility;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

public class WatchAccountService extends Service<WatchAccountEntity> {

    public WatchAccountService() {
        super(new WatchAccountRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<WatchAccountEntity> entityType) throws SQLException {
        String id = apiGatewayRequest.getPathParameters().get("id");
        String type = apiGatewayRequest.getPathParameters().get("type");
        List<WatchAccountEntity> dataList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put(type.equals("account") ? "account_id" : "watch_id", id);
        }});
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}
