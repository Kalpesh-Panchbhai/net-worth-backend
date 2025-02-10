package com.networth.networthbackend.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RepositoryImpl<T> {

    void delete(String id);

    Optional<T> getByPrimaryField(String id) throws SQLException;

    List<T> generateEntityData(ResultSet rs) throws SQLException;

    List<T> getBySecondaryField(String fieldName, String fieldValue);
}
