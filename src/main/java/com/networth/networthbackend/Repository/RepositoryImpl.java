package com.networth.networthbackend.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public interface RepositoryImpl<T> {

    int save(T data) throws SQLException;

    int update(T data) throws SQLException;

    void delete(Object fieldName, Object fieldValue) throws SQLException;

    Optional<T> getByPrimaryField(Integer id) throws SQLException;

    List<T> getBySecondaryField(LinkedHashMap<String, Object> queryMap) throws SQLException;

    List<T> generateEntityData(ResultSet rs) throws SQLException;

    PreparedStatement prepareInsertQuery(T data) throws SQLException;

    PreparedStatement prepareUpdateQuery(T data) throws SQLException;
}
