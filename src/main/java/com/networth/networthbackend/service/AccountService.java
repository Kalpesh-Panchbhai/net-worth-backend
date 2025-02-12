package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.WatchAccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.List;

public class AccountService extends Service<AccountEntity> {

    private final String ALL_WATCH_LIST_NAME = "All";

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldNameList = List.of("user_id");
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountEntity> entityType) {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = super.post(apiGatewayRequest, context, entityType);
        RestResponse restResponse = new Utility<RestResponse>().convertStringToObj(apiGatewayProxyResponseEvent.getBody(), context, RestResponse.class);
        WatchEntity defaultWatchList = getDefaultWatchList();
        addAccountToDefaultWatchList(defaultWatchList, restResponse.getId());
        return apiGatewayProxyResponseEvent;
    }

    private WatchEntity getDefaultWatchList() {
        WatchService watchService = new WatchService();
        List<WatchEntity> watchEntityList = watchService.repository.getBySecondaryField(List.of("name"), ALL_WATCH_LIST_NAME);
        return watchEntityList.get(0);
    }

    private void addAccountToDefaultWatchList(WatchEntity watchEntity, Integer accountId) {
        WatchAccountService watchAccountService = new WatchAccountService();
        WatchAccountEntity watchAccountEntity = new WatchAccountEntity();
        watchAccountEntity.setAccountId(accountId);
        watchAccountEntity.setWatchId(watchEntity.getId());
        watchAccountService.repository.save(watchAccountEntity);
    }
}
