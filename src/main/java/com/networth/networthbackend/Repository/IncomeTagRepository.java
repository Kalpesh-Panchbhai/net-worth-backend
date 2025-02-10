package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTagEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class IncomeTagRepository extends Repository<IncomeTagEntity> {

    public IncomeTagRepository() {
        tableName = "income_tag";
    }

    @Override
    public List<IncomeTagEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
