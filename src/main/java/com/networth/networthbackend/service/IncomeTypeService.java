package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTypeRepository;
import com.networth.networthbackend.entity.IncomeTypeEntity;

import java.util.LinkedHashMap;
import java.util.List;

public class IncomeTypeService extends Service<IncomeTypeEntity> {

    public IncomeTypeService() {
        super(new IncomeTypeRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeTypeEntity> entityType) {
        IncomeTypeEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        if (requestData.getIsDefault()) {
            updateDefaultRecordToFalse(requestData);
            return super.post(apiGatewayRequest, context, entityType);
        } else {
            return super.post(apiGatewayRequest, context, entityType);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<IncomeTypeEntity> entityType) {
        IncomeTypeEntity requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, entityType);
        updateDefaultRecordToFalse(requestData);
        return super.patch(apiGatewayRequest, context, entityType);
    }

    private void updateDefaultRecordToFalse(IncomeTypeEntity requestData) {
        List<IncomeTypeEntity> incomeTypeEntityList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", requestData.getUserId());
            put("is_default", requestData.getIsDefault());
        }});
        for (IncomeTypeEntity incomeTypeEntity : incomeTypeEntityList) {
            incomeTypeEntity.setIsDefault(false);
            repository.update(incomeTypeEntity);
        }
    }
}
