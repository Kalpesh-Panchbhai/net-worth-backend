package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.ChartDataRepository;
import com.networth.networthbackend.entity.ChartDataEntity;
import com.networth.networthbackend.model.ChartDataRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChartDataService extends Service<ChartDataRest, ChartDataEntity> {

    public ChartDataService() {
        super(new ChartDataRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<ChartDataRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<ChartDataRest> chartDataRestList = getAllChartList(id);
        String jsonBody = utilityRest.convertListOfObjToString(chartDataRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<ChartDataRest> getAllChartList(String accountId) {
        ScanResult scanResult = repository.getBySecondaryField("accountId", accountId);
        List<ChartDataRest> chartDataRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            ChartDataRest chartDataRest = new ChartDataRest();
            chartDataRest.setId(attributeValueMap.get("id").getS());
            chartDataRest.setAccountId(attributeValueMap.get("accountId").getS());
            chartDataRest.setDate(attributeValueMap.get("date").getS());
            chartDataRest.setValue(Double.parseDouble(attributeValueMap.get("value").getN()));
            chartDataRestList.add(chartDataRest);
        }
        return chartDataRestList;
    }

    @Override
    public ChartDataEntity convertRequestToEntity(ChartDataRest data) {
        ChartDataEntity chartDataEntity = new ChartDataEntity();
        chartDataEntity.setId(data.getId());
        chartDataEntity.setAccountId(data.getAccountId());
        chartDataEntity.setDate(data.getDate());
        chartDataEntity.setValue(data.getValue());
        return chartDataEntity;
    }
}
