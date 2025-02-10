package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.WatchRepository;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.WatchRest;

public class WatchService extends Service<WatchRest, WatchEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();

    public WatchService() {
        super(new WatchRepository());
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
