package com.networth.networthbackend.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RepositoryImpl<T> {

    int save(T data, Boolean disconnect);

    int update(T data, Boolean disconnect);

    void delete(Integer id, Boolean disconnect);

    Optional<T> getByPrimaryField(String id, Boolean disconnect) throws SQLException;

    List<T> getBySecondaryField(List<String> fieldNameList, String fieldValue, Boolean disconnect);

    List<T> generateEntityData(ResultSet rs) throws SQLException;

    PreparedStatement prepareInsertQuery(T data) throws SQLException;

    PreparedStatement prepareUpdateQuery(T data) throws SQLException;
}
