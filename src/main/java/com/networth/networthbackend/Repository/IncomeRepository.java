package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class IncomeRepository extends Repository<IncomeEntity> {

    public IncomeRepository() {
        tableName = "income";
    }

    @Override
    public List<IncomeEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
