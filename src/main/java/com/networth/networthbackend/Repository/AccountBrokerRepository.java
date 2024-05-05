package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;

public class AccountBrokerRepository extends Repository<AccountBrokerEntity> {

    public AccountBrokerRepository() {
        tableName = "account_broker";
    }
}
