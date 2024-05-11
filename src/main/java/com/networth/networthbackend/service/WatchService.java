package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.WatchRepository;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.WatchRest;
import com.networth.networthbackend.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WatchService extends Service<WatchRest, WatchEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();

    public WatchService() {
        super(new WatchRepository());
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<WatchRest> type) {
        String id = apiGatewayRequest.getPathParameters().get("id");
        List<WatchRest> watchRestList = getWatchList(id);
        String jsonBody = utilityRest.convertListOfObjToString(watchRestList, context);
        context.getLogger().log("data fetched successfully from dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public List<WatchRest> getWatchList(String userId) {
        ScanResult scanResult = repository.getBySecondaryField("userId", userId);
        List<WatchRest> watchRestList = new ArrayList<>();
        for (Map<String, AttributeValue> attributeValueMap : scanResult.getItems()) {
            List<String> accountList = attributeValueMap.get("accountList").getL().stream().map(AttributeValue::getS).collect(Collectors.toList());
            WatchRest watchRest = new WatchRest(attributeValueMap.get("id").getS(), attributeValueMap.get("userId").getS(), attributeValueMap.get("name").getS(), accountList);
            watchRestList.add(watchRest);
        }
        return watchRestList;
    }

    @Override
    public WatchEntity convertRequestToEntity(WatchRest accountRestRequest) {
        WatchEntity watchEntity = new WatchEntity();
        watchEntity.setId(accountRestRequest.getId());
        watchEntity.setUserId(accountRestRequest.getUserId());
        watchEntity.setName(accountRestRequest.getName());
        watchEntity.setAccountList(accountRestRequest.getAccountList());
        return watchEntity;
    }
}
