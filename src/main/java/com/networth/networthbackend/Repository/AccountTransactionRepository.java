package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;

public class AccountTransactionRepository extends Repository<AccountTransactionEntity> {

    public AccountTransactionRepository() {
        tableName = "account_transaction";
    }
}
