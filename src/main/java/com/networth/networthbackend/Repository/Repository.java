package com.networth.networthbackend.Repository;

import com.networth.networthbackend.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class Repository<T> implements RepositoryImpl<T> {

    public final DatabaseService databaseService;
    public final Connection connection;
    public String tableName;

    public Repository() {
        databaseService = new DatabaseService();
        connection = databaseService.getConnection();
    }

    @Override
    public int save(T data) {
        try {
            PreparedStatement preparedStatement = prepareInsertQuery(data);
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    @Override
    public int update(T data) {
        try {
            PreparedStatement preparedStatement = prepareUpdateQuery(data);
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    @Override
    public void delete(Integer id) {
        try {
            String query = "delete from " + tableName + " where id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setInt(1, id);
            pstmt.execute();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public Optional<T> getByPrimaryField(String id) {
        try {
            String query = "select * from " + tableName + " where id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            List<T> list = generateEntityData(rs);
            return !list.isEmpty() ? Optional.of(list.get(0)) : Optional.empty();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    @Override
    public List<T> getBySecondaryField(LinkedHashMap<String, Object> queryMap) {
        try {
            StringBuilder query = new StringBuilder("select * from " + tableName + " where (");
            boolean multipleFields = false;
            for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
                if (multipleFields) query.append(" AND ").append(entry.getKey()).append(" = ?");
                else query.append(entry.getKey()).append(" = ?");
                multipleFields = true;
            }
            query.append(")");
            PreparedStatement pstmt = connection.prepareStatement(String.valueOf(query));
            int queryId = 1;
            for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
                pstmt.setObject(queryId++, entry.getValue());
            }
            ResultSet rs = pstmt.executeQuery();
            return generateEntityData(rs);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}
