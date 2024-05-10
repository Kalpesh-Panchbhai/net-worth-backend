package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBalanceEntity;

public class AccountBalanceRepository extends Repository<AccountBalanceEntity> {

    public AccountBalanceRepository() {
        tableName = "account_balance";
    }
}
