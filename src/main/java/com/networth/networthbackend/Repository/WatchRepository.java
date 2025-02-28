package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountValueEntity;
import com.networth.networthbackend.entity.WatchAccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.service.AccountService;
import com.networth.networthbackend.service.WatchAccountService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public class WatchRepository extends Repository<WatchEntity> {

    public WatchRepository() {
        tableName = "watch";
    }

    @Override
    public PreparedStatement prepareInsertQuery(WatchEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, name) VALUES (?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setString(2, data.getName());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(WatchEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET user_id = ?, name = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setInt(3, data.getId());
        return preparedStatement;
    }

    @Override
    public List<WatchEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<WatchEntity> watchEntityList = new ArrayList<>();
        while (rs.next()) {
            WatchEntity watchEntity = new WatchEntity();
            watchEntity.setId(rs.getInt("id"));
            watchEntity.setUserId(rs.getInt("user_id"));
            watchEntity.setName(rs.getString("name"));
            getOtherDetails(watchEntity.getId(), watchEntity);
            watchEntityList.add(watchEntity);
        }
        return watchEntityList;
    }

    private void getOtherDetails(Integer watchId, WatchEntity watchEntity) throws SQLException {
        WatchAccountService watchAccountService = new WatchAccountService();
        List<WatchAccountEntity> dataList = watchAccountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("watch_id", watchId);
        }});
        watchEntity.setTotalAccount(dataList.size());
        AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);
        AccountService accountService = new AccountService();
        for (WatchAccountEntity watchAccountEntity : dataList) {
            Optional<AccountEntity> accountEntity = accountService.repository.getByPrimaryField(watchAccountEntity.getAccountId());
            accountEntity.ifPresent(accountEntity1 -> {
                accountValueEntity.setCurrentValue(accountValueEntity.getCurrentValue() + accountEntity1.getValue().getCurrentValue());
                accountValueEntity.setPreviousValue(accountValueEntity.getPreviousValue() + accountEntity1.getValue().getPreviousValue());
            });
        }
        watchEntity.setValue(accountValueEntity);
        accountService.repository.databaseService.disconnect();
        watchAccountService.repository.databaseService.disconnect();
    }
}
