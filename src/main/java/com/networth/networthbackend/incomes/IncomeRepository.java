package com.networth.networthbackend.incomes;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Slf4j
public class IncomeRepository extends JdbcRepository<Income> {

    private static final RowMapper<Income> MAPPER = rs -> Income.builder()
            .id(rs.getLong(Constants.DB.Incomes.COL_ID))
            .userId(rs.getLong(Constants.DB.Incomes.COL_USER_ID))
            .incomeSourceId(rs.getLong(Constants.DB.Incomes.COL_INCOME_SOURCE_ID))
            .incomeTagId(rs.getLong(Constants.DB.Incomes.COL_INCOME_TAG_ID))
            .netAmount(rs.getBigDecimal(Constants.DB.Incomes.COL_NET_AMOUNT))
            .taxPaid(rs.getBigDecimal(Constants.DB.Incomes.COL_TAX_PAID))
            .currency(rs.getString(Constants.DB.Incomes.COL_CURRENCY))
            .creditedDate(rs.getTimestamp(Constants.DB.Incomes.COL_CREDITED_DATE).toInstant())
            .build();

    public List<Income> listByUserId(long userId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                    Constants.DB.Incomes.COL_ID,
                    Constants.DB.Incomes.COL_USER_ID,
                    Constants.DB.Incomes.COL_INCOME_SOURCE_ID,
                    Constants.DB.Incomes.COL_INCOME_TAG_ID,
                    Constants.DB.Incomes.COL_NET_AMOUNT,
                    Constants.DB.Incomes.COL_TAX_PAID,
                    Constants.DB.Incomes.COL_CURRENCY,
                    Constants.DB.Incomes.COL_CREDITED_DATE)
                + Constants.SQL.FROM + Constants.DB.Incomes.TABLE
                + Constants.SQL.WHERE + Constants.DB.Incomes.COL_USER_ID + Constants.SQL.EQ_PARAM;
        return queryList(sql, ps -> ps.setLong(1, userId), MAPPER);
    }

    public Income create(long userId,
                         long incomeSourceId,
                         long incomeTagId,
                         BigDecimal netAmount,
                         BigDecimal taxPaid,
                         String currency,
                         Instant creditedDate) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.Incomes.TABLE
                + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE,
                    Constants.DB.Incomes.COL_USER_ID,
                    Constants.DB.Incomes.COL_INCOME_SOURCE_ID,
                    Constants.DB.Incomes.COL_INCOME_TAG_ID,
                    Constants.DB.Incomes.COL_NET_AMOUNT,
                    Constants.DB.Incomes.COL_TAX_PAID,
                    Constants.DB.Incomes.COL_CURRENCY,
                    Constants.DB.Incomes.COL_CREDITED_DATE)
                + Constants.SQL.CLOSE_PAREN
                + Constants.SQL.VALUES
                + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM,
                    Constants.SQL.PARAM) + Constants.SQL.CLOSE_PAREN;

        long id = 0L;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, incomeSourceId);
            ps.setLong(3, incomeTagId);
            ps.setBigDecimal(4, netAmount);
            ps.setBigDecimal(5, taxPaid);
            ps.setString(6, currency);
            ps.setTimestamp(7, Timestamp.from(creditedDate));
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.INCOME_INSERT_AFFECTED, affected, userId, incomeSourceId, incomeTagId);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getLong(1);
            }
        }
        Income income = Income.builder()
                .id(id)
                .userId(userId)
                .incomeSourceId(incomeSourceId)
                .incomeTagId(incomeTagId)
                .netAmount(netAmount)
                .taxPaid(taxPaid)
                .currency(currency)
                .creditedDate(creditedDate)
                .build();
        log.info(Constants.Logs.INCOME_CREATED, income.getId(), userId, incomeSourceId, incomeTagId, netAmount, taxPaid, currency, creditedDate);
        return income;
    }
}
