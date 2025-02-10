package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTypeRepository;
import com.networth.networthbackend.entity.IncomeTypeEntity;
import com.networth.networthbackend.model.TypeRest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class IncomeTypeService extends Service<TypeRest, IncomeTypeEntity> {

    public IncomeTypeService() {
        super(new IncomeTypeRepository());
        secondaryFieldName = "user_id";
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        if (requestData.getIsDefault()) {
            makeOtherRecordNonDefault(requestData.getUserId());
        }
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public IncomeTypeEntity convertRequestToEntity(TypeRest data) {
        return new IncomeTypeEntity(data.getId(), data.getUserId(), data.getName(), data.getIsDefault());
    }

    private void makeOtherRecordNonDefault(String userId) {
        List<IncomeTypeEntity> incomeTypeEntityList = repository.getBySecondaryField("user_id", userId);
        for (IncomeTypeEntity incomeTypeEntity : incomeTypeEntityList) {
            if (incomeTypeEntity.getIsDefault()) {
                repository.update(new IncomeTypeEntity(incomeTypeEntity.getId(), incomeTypeEntity.getUserId(), incomeTypeEntity.getName(), false));
            }
        }
    }
}
