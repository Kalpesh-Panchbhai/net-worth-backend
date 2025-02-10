package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.model.AccountRest;

public class AccountService extends Service<AccountRest, AccountEntity> {

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldName = "user_id";
    }
}
