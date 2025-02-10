package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.UserEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserRepository extends Repository<UserEntity> {

    public UserRepository() {
        tableName = "user";
    }

    @Override
    public List<UserEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<UserEntity> list = new ArrayList<>();
        while (rs.next()) {
            UserEntity userEntity = new UserEntity();
            userEntity.setId(rs.getString("id"));
            list.add(userEntity);
        }
        return list;
    }
}
