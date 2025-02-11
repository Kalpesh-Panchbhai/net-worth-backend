package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.UserEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserRepository extends Repository<UserEntity> {

    public UserRepository() {
        tableName = "user";
    }

    @Override
    public PreparedStatement prepareInsertQuery(UserEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(id) VALUES (?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getId());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(UserEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET id = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getId());
        preparedStatement.setString(2, data.getId());
        return preparedStatement;
    }

    @Override
    public List<UserEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<UserEntity> userEntityList = new ArrayList<>();
        while (rs.next()) {
            UserEntity userEntity = new UserEntity();
            userEntity.setId(rs.getString("id"));
            userEntityList.add(userEntity);
        }
        return userEntityList;
    }
}
