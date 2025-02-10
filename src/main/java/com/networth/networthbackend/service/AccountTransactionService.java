package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.Repository;
import com.networth.networthbackend.model.AccountTransactionRest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AccountTransactionService extends Service<AccountTransactionRest, Void> {

    public AccountTransactionService() {
        super(new Repository<>() {
            @Override
            public List<Void> generateEntityData(ResultSet rs) throws SQLException {
                return List.of();
            }
        });
    }
}
