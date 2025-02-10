package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.model.AccountBrokerRest;

public class AccountBrokerService extends Service<AccountBrokerRest, AccountBrokerEntity> {

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
        secondaryFieldName = "account_id";
    }
}
