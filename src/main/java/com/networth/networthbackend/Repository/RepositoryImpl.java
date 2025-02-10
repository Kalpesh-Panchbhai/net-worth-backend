package com.networth.networthbackend.Repository;

import com.amazonaws.services.dynamodbv2.model.ScanResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RepositoryImpl<T> {

    void save(T data);

    void update(T data);

    void delete(String id);

    T getByPrimaryField(String id, Class<T> type);

    Optional<T> getByPrimaryField(String id) throws SQLException;

    List<T> generateEntityData(ResultSet rs) throws SQLException;

    ScanResult getBySecondaryField(String fieldName, String fieldValue);
}
