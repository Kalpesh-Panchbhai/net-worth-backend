package com.networth.networthbackend.transactions;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class TransactionRepository extends JdbcRepository<Transaction> {

    private static final RowMapper<Transaction> MAPPER = rs -> Transaction.builder()
            .id(rs.getLong(Constants.DB.Transactions.COL_ID))
            .accountId(rs.getLong(Constants.DB.Transactions.COL_ACCOUNT_ID))
            .holdingId(rs.getLong(Constants.DB.Transactions.COL_HOLDING_ID))
            .txnDate(rs.getDate(Constants.DB.Transactions.COL_TXN_DATE).toLocalDate())
            .invested(rs.getBigDecimal(Constants.DB.Transactions.COL_INVESTED))
            .value(rs.getBigDecimal(Constants.DB.Transactions.COL_VALUE))
            .build();

    public Transaction create(long accountId, long holdingId, LocalDate txnDate, BigDecimal invested, BigDecimal value) throws SQLException {
        final String sql = Constants.SQL.INSERT_INTO + Constants.DB.Transactions.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Transactions.COL_ACCOUNT_ID,
                Constants.DB.Transactions.COL_HOLDING_ID,
                Constants.DB.Transactions.COL_TXN_DATE,
                Constants.DB.Transactions.COL_INVESTED,
                Constants.DB.Transactions.COL_VALUE) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?,?,?)";
        long id = executeAndReturnGeneratedId(sql, ps -> {
            ps.setLong(1, accountId);
            ps.setLong(2, holdingId);
            ps.setDate(3, Date.valueOf(txnDate));
            ps.setBigDecimal(4, invested);
            ps.setBigDecimal(5, value);
        });
        return Transaction.builder()
                .id(id)
                .accountId(accountId)
                .holdingId(holdingId)
                .txnDate(txnDate)
                .invested(invested)
                .value(value)
                .build();
    }

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
                + Constants.SQL.ORDER_BY + Constants.DB.Transactions.COL_TXN_DATE + Constants.SQL.DESC;
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
                + Constants.SQL.ORDER_BY + Constants.DB.Transactions.COL_TXN_DATE + Constants.SQL.DESC;
        return queryList(sql, ps -> ps.setLong(1, holdingId), MAPPER);
    }

    public Optional<Transaction> findLatestByHoldingId(long holdingId) throws SQLException {
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
                + Constants.SQL.ORDER_BY + Constants.DB.Transactions.COL_TXN_DATE + Constants.SQL.DESC
                + Constants.SQL.LIMIT_1;
        return queryOne(sql, ps -> ps.setLong(1, holdingId), MAPPER);
    }

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Transactions.TABLE
                + Constants.SQL.WHERE + Constants.DB.Transactions.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        return execute(sql, ps -> ps.setLong(1, id));
    }
}
