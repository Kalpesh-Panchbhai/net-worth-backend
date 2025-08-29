package com.networth.networthbackend.transactions;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;

import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

public class TransactionRepository extends JdbcRepository<Transaction> {

    private static final RowMapper<Transaction> MAPPER = rs -> Transaction.builder()
            .id(rs.getLong(Constants.DB.Transactions.COL_ID))
            .accountId(rs.getLong(Constants.DB.Transactions.COL_ACCOUNT_ID))
            .holdingId(rs.getLong(Constants.DB.Transactions.COL_HOLDING_ID))
            .txnDate(rs.getDate(Constants.DB.Transactions.COL_TXN_DATE).toLocalDate())
            .invested(rs.getBigDecimal(Constants.DB.Transactions.COL_INVESTED))
            .value(rs.getBigDecimal(Constants.DB.Transactions.COL_VALUE))
            .build();

    public List<Transaction> listByAccountId(long accountId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Transactions.COL_ID,
                Constants.DB.Transactions.COL_ACCOUNT_ID,
                Constants.DB.Transactions.COL_HOLDING_ID,
                Constants.DB.Transactions.COL_TXN_DATE,
                Constants.DB.Transactions.COL_INVESTED,
                Constants.DB.Transactions.COL_VALUE)
                + Constants.SQL.FROM + Constants.DB.Transactions.TABLE
                + Constants.SQL.WHERE + Constants.DB.Transactions.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM
                + " ORDER BY " + Constants.DB.Transactions.COL_TXN_DATE + " DESC";
        return queryList(sql, ps -> ps.setLong(1, accountId), MAPPER);
    }

    public List<Transaction> listByHoldingId(long holdingId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Transactions.COL_ID,
                Constants.DB.Transactions.COL_ACCOUNT_ID,
                Constants.DB.Transactions.COL_HOLDING_ID,
                Constants.DB.Transactions.COL_TXN_DATE,
                Constants.DB.Transactions.COL_INVESTED,
                Constants.DB.Transactions.COL_VALUE)
                + Constants.SQL.FROM + Constants.DB.Transactions.TABLE
                + Constants.SQL.WHERE + Constants.DB.Transactions.COL_HOLDING_ID + Constants.SQL.EQ_PARAM
                + " ORDER BY " + Constants.DB.Transactions.COL_TXN_DATE + " DESC";
        return queryList(sql, ps -> ps.setLong(1, holdingId), MAPPER);
    }

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Transactions.TABLE
                + Constants.SQL.WHERE + Constants.DB.Transactions.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        return execute(sql, ps -> ps.setLong(1, id));
    }
}
