package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.InvestRepository;
import com.networth.networthbackend.Repository.WatchAccountRepository;
import com.networth.networthbackend.entity.InvestEntity;
import com.networth.networthbackend.entity.WatchAccountEntity;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class InvestService extends Service<InvestEntity> {

    public InvestService() {
        super(new InvestRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<InvestEntity> entityType) throws SQLException {
        InvestEntity investEntity = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        if (investEntity.getOperation().equalsIgnoreCase("update")) {
            return super.post(apiGatewayRequest, context, entityType);
        } else {
            List<InvestEntity> dataList = repository.getBySecondaryField(new LinkedHashMap<>() {{
                put("account_broker_id", investEntity.getAccountBrokerId());
            }});
            dataList.sort(Comparator.comparing(InvestEntity::getDate));

            investEntity.setValue(dataList.isEmpty() ? investEntity.getValue() : dataList.get(dataList.size() - 1).getValue() + investEntity.getValue());
            Integer id = repository.save(investEntity);
            RestResponse restResponse = Utility.generateRestResponse("success", id);
            String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
            context.getLogger().log("data added successfully to database:::" + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
        }
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<InvestEntity> entityType) throws SQLException {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<InvestEntity> dataList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_broker_id", id);
        }});
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }
}
