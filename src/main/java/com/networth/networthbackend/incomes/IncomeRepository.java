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
import java.sql.Date;
import java.time.LocalDate;
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
            .creditedDate(rs.getDate(Constants.DB.Incomes.COL_CREDITED_DATE).toLocalDate())
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

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Incomes.TABLE
                + Constants.SQL.WHERE + Constants.DB.Incomes.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        int affected = execute(sql, ps -> ps.setLong(1, id));
        log.debug(Constants.Logs.INCOME_DELETE_AFFECTED, affected, id);
        return affected;
    }

    public java.util.Optional<Income> findById(long id) throws SQLException {
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
                + Constants.SQL.WHERE + Constants.DB.Incomes.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        return queryOne(sql, ps -> ps.setLong(1, id), MAPPER);
    }

    public int updateById(long id,
                          Long incomeSourceId,
                          Long incomeTagId,
                          BigDecimal netAmount,
                          BigDecimal taxPaid,
                          String currency,
                          LocalDate creditedDate) throws SQLException {
        StringBuilder set = new StringBuilder();
        java.util.List<java.util.function.BiConsumer<java.sql.PreparedStatement, Integer>> binders = new java.util.ArrayList<>();

        if (incomeSourceId != null) {
            appendSet(set, Constants.DB.Incomes.COL_INCOME_SOURCE_ID);
            binders.add((ps, idx) -> { try { ps.setLong(idx, incomeSourceId); } catch (SQLException e) { throw new RuntimeException(e); } });
        }
        if (incomeTagId != null) {
            appendSet(set, Constants.DB.Incomes.COL_INCOME_TAG_ID);
            binders.add((ps, idx) -> { try { ps.setLong(idx, incomeTagId); } catch (SQLException e) { throw new RuntimeException(e); } });
        }
        if (netAmount != null) {
            appendSet(set, Constants.DB.Incomes.COL_NET_AMOUNT);
            binders.add((ps, idx) -> { try { ps.setBigDecimal(idx, netAmount); } catch (SQLException e) { throw new RuntimeException(e); } });
        }
        if (taxPaid != null) {
            appendSet(set, Constants.DB.Incomes.COL_TAX_PAID);
            binders.add((ps, idx) -> { try { ps.setBigDecimal(idx, taxPaid); } catch (SQLException e) { throw new RuntimeException(e); } });
        }
        if (currency != null) {
            appendSet(set, Constants.DB.Incomes.COL_CURRENCY);
            binders.add((ps, idx) -> { try { ps.setString(idx, currency); } catch (SQLException e) { throw new RuntimeException(e); } });
        }
        if (creditedDate != null) {
            appendSet(set, Constants.DB.Incomes.COL_CREDITED_DATE);
            binders.add((ps, idx) -> { try { ps.setDate(idx, Date.valueOf(creditedDate)); } catch (SQLException e) { throw new RuntimeException(e); } });
        }

        if (binders.isEmpty()) return 0; // nothing to update

        final String sql = Constants.SQL.UPDATE + Constants.DB.Incomes.TABLE
                + Constants.SQL.SET + set
                + Constants.SQL.WHERE + Constants.DB.Incomes.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;

        return execute(sql, ps -> {
            for (int i = 0; i < binders.size(); i++) {
                binders.get(i).accept(ps, i + 1);
            }
            ps.setLong(binders.size() + 1, id);
        });
    }

    private static void appendSet(StringBuilder set, String column) {
        if (set.length() > 0) set.append(Constants.SQL.COMMA_SPACE);
        set.append(column).append(" = ").append(Constants.SQL.PARAM);
    }

    public Income create(long userId,
                         long incomeSourceId,
                         long incomeTagId,
                         BigDecimal netAmount,
                         BigDecimal taxPaid,
                         String currency,
                         LocalDate creditedDate) throws SQLException {
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
            ps.setDate(7, Date.valueOf(creditedDate));
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
