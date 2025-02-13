package com.networth.networthbackend.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RepositoryImpl<T> {

    int save(T data);

    int update(T data);

    void delete(Integer id);

    Optional<T> getByPrimaryField(String id) throws SQLException;

    List<T> getBySecondaryField(LinkedHashMap<String, Object> queryMap);

    List<T> generateEntityData(ResultSet rs) throws SQLException;

    PreparedStatement prepareInsertQuery(T data) throws SQLException;

    PreparedStatement prepareUpdateQuery(T data) throws SQLException;
}
