package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.WatchRepository;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.WatchRest;

public class WatchService extends Service<WatchRest, WatchEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();

    public WatchService() {
        super(new WatchRepository());
        secondaryFieldName = "user_id";
    }
}
