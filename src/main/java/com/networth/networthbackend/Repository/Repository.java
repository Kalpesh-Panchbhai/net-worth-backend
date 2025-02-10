package com.networth.networthbackend.Repository;

import com.networth.networthbackend.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

public abstract class Repository<T> implements RepositoryImpl<T> {

    public final DatabaseService databaseService;
    private final Connection connection;
    public String tableName;

    public Repository() {
        databaseService = new DatabaseService();
        connection = databaseService.getConnection();
    }

    @Override
    public void delete(String id) {
        try {
            String query = "delete from " + tableName + " where id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, id);
            pstmt.execute();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            databaseService.disconnect();
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
        } finally {
            databaseService.disconnect();
        }
        return null;
    }

    @Override
    public List<T> getBySecondaryField(String fieldName, String fieldValue) {
        try {
            String query = "select * from " + tableName + " where " + fieldName + " = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, fieldValue);
            ResultSet rs = pstmt.executeQuery();
            return generateEntityData(rs);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            databaseService.disconnect();
        }
        return null;
    }
}
