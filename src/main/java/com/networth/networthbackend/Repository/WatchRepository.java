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
            fetchAccountValueEntity(watchEntity);
            watchEntityList.add(watchEntity);
        }
        return watchEntityList;
    }

    private void fetchAccountValueEntity(WatchEntity watchEntity) throws SQLException {
        WatchAccountService watchAccountService = new WatchAccountService();
        AccountService accountService = new AccountService();
        AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);
        List<WatchAccountEntity> watchAccountEntityList = watchAccountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("watch_id", watchEntity.getId());
        }});
        for (WatchAccountEntity watchAccountEntity : watchAccountEntityList) {
            Optional<AccountEntity> accountEntity = accountService.repository.getByPrimaryField(watchAccountEntity.getAccountId());
            if (accountEntity.isPresent()) {
                accountValueEntity.setCurrentBalance(accountValueEntity.getCurrentBalance() + accountEntity.get().getValue().getCurrentBalance());
                accountValueEntity.setPreviousBalance(accountValueEntity.getPreviousBalance() + accountEntity.get().getValue().getPreviousBalance());
            }
        }
        watchEntity.setTotalAccount(watchAccountEntityList.size());
        watchEntity.setValue(accountValueEntity);
    }
}
