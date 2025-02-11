package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;

import java.util.List;

public class AccountService extends Service<AccountEntity> {

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldNameList = List.of("user_id");
    }
}
