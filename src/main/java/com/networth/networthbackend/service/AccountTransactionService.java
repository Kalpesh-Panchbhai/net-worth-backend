package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountTransactionRepository;
import com.networth.networthbackend.entity.AccountTransactionEntity;

import java.util.List;

public class AccountTransactionService extends Service<AccountTransactionEntity> {

    public AccountTransactionService() {
        super(new AccountTransactionRepository());
        secondaryFieldNameList = List.of("account_id", "account_broker_id");
    }
}
