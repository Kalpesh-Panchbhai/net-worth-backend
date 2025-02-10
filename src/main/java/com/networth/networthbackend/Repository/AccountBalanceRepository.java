package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBalanceEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AccountBalanceRepository extends Repository<AccountBalanceEntity> {

    public AccountBalanceRepository() {
        tableName = "account_balance";
    }

    @Override
    public PreparedStatement generateInsertQuery(AccountBalanceEntity data) throws SQLException {
        return null;
    }

    @Override
    public List<AccountBalanceEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
