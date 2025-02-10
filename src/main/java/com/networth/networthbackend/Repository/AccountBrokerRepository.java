package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountBrokerRepository extends Repository<AccountBrokerEntity> {

    public AccountBrokerRepository() {
        tableName = "account_broker";
    }

    @Override
    public List<AccountBrokerEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountBrokerEntity> list = new ArrayList<>();
        while (rs.next()) {
            AccountBrokerEntity accountBrokerEntity = new AccountBrokerEntity();
            accountBrokerEntity.setId(rs.getString("id"));
            accountBrokerEntity.setAccountId(rs.getString("account_id"));
            accountBrokerEntity.setName(rs.getString("name"));
            accountBrokerEntity.setSymbol(rs.getString("symbol"));
            list.add(accountBrokerEntity);
        }
        return list;
    }
}
