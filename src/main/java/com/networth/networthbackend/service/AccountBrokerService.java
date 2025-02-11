package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;

import java.util.List;

public class AccountBrokerService extends Service<AccountBrokerEntity> {

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
        secondaryFieldNameList = List.of("account_id");
    }
}
