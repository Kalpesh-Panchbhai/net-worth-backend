package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountBrokerRepository;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.model.AccountBrokerRest;

public class AccountBrokerService extends Service<AccountBrokerRest, AccountBrokerEntity> {

    public AccountBrokerService() {
        super(new AccountBrokerRepository());
        secondaryFieldName = "account_id";
    }

    @Override
    public AccountBrokerEntity convertRequestToEntity(AccountBrokerRest data) {
        return new AccountBrokerEntity(data.getId(), data.getAccountId(), data.getSymbol(), data.getName());
    }
}
