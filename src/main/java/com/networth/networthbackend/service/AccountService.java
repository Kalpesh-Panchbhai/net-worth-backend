package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.model.AccountRest;

public class AccountService extends Service<AccountRest, AccountEntity> {

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldName = "user_id";
    }

    @Override
    public AccountEntity convertRequestToEntity(AccountRest accountRestRequest) {
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setId(accountRestRequest.getId());
        accountEntity.setUserId(accountRestRequest.getUserId());
        accountEntity.setCurrency(accountRestRequest.getCurrency());
        accountEntity.setActive(accountRestRequest.getActive());
        return accountEntity;
    }
}
