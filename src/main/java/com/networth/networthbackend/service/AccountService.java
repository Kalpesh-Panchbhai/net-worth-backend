package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;

public class AccountService extends Service<AccountEntity> {

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldName = "user_id";
    }
}
