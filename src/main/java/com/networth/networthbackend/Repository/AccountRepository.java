package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;

public class AccountRepository extends Repository<AccountEntity> {

    public AccountRepository() {
        tableName = "account";
    }
}
