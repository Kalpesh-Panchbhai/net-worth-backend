package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;

public class IncomeTagService extends Service<IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeTagEntity> entityType) throws SQLException {
        IncomeTagEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        if (requestData.getIsDefault()) {
            updateDefaultRecordToFalse(requestData);
            return super.post(apiGatewayRequest, context, entityType);
        } else {
            return super.post(apiGatewayRequest, context, entityType);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeTagEntity> entityType) throws SQLException {
        IncomeTagEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        updateDefaultRecordToFalse(requestData);
        return super.patch(apiGatewayRequest, context, entityType);
    }

    private void updateDefaultRecordToFalse(IncomeTagEntity requestData) throws SQLException {
        List<IncomeTagEntity> incomeTagEntityList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", requestData.getUserId());
            put("is_default", requestData.getIsDefault());
        }});
        for (IncomeTagEntity incomeTagEntity : incomeTagEntityList) {
            incomeTagEntity.setIsDefault(false);
            repository.update(incomeTagEntity);
        }
    }
}
