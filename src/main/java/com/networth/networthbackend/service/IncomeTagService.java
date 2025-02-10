package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;
import com.networth.networthbackend.model.TypeRest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class IncomeTagService extends Service<TypeRest, IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
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
    public APIGatewayProxyResponseEvent patch(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<TypeRest> type) throws IOException, URISyntaxException, InterruptedException {
        TypeRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        makeOtherRecordNonDefault(requestData.getUserId());
        return super.post(apiGatewayRequest, context, type);
    }

    @Override
    public IncomeTagEntity convertRequestToEntity(TypeRest data) {
        return new IncomeTagEntity(data.getId(), data.getUserId(), data.getName(), data.getIsDefault());
    }

    private void makeOtherRecordNonDefault(String userId) {
        List<IncomeTagEntity> incomeTagEntityList = repository.getBySecondaryField("user_id", userId);
        for (IncomeTagEntity incomeTagEntity : incomeTagEntityList) {
            if (incomeTagEntity.getIsDefault()) {
                repository.update(new IncomeTagEntity(incomeTagEntity.getId(), incomeTagEntity.getUserId(), incomeTagEntity.getName(), false));
            }
        }
    }
}
