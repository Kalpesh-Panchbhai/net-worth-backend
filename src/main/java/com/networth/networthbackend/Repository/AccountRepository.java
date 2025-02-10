package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountRepository extends Repository<AccountEntity> {

    public AccountRepository() {
        tableName = "account";
    }

    @Override
    public List<AccountEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountEntity> list = new ArrayList<>();
        while (rs.next()) {
            AccountEntity accountEntity = new AccountEntity();
            accountEntity.setId(rs.getString("id"));
            accountEntity.setUserId(rs.getString("user_id"));
            accountEntity.setName(rs.getString("name"));
            accountEntity.setType(rs.getString("type"));
            accountEntity.setActive(rs.getBoolean("is_active"));
            accountEntity.setCurrency(rs.getString("currency"));
            list.add(accountEntity);
        }
        return list;
    }
}
