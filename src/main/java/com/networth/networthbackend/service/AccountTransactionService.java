package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.Repository;
import com.networth.networthbackend.model.AccountTransactionRest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AccountTransactionService extends Service<AccountTransactionRest, Void> {

    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountBalanceService accountBalanceService = new AccountBalanceService();

    public AccountTransactionService() {
        super(new Repository<>() {
            @Override
            public List<Void> generateEntityData(ResultSet rs) throws SQLException {
                return List.of();
            }
        });
    }

    @Override
    public Void convertRequestToEntity(AccountTransactionRest data) {
        return null;
    }
}
