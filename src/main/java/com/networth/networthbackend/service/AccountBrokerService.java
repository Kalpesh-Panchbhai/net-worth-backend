package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;

public class AccountBrokerService extends Service<AccountBrokerEntity> {

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
    }
}
