package com.networth.networthbackend.charts;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.db.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ChartRepository extends JdbcRepository<ChartPoint> {

    public List<ChartPoint> listForHolding(long holdingId, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder df = new StringBuilder();
        if (from != null)
            df.append("    AND d.").append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" >= ?\n");
        if (to != null)
            df.append("    AND d.").append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" <= ?\n");
        String sql = Constants.DB.AccountHoldingValuesDaily.SQL_LIST_FOR_HOLDING_CHART.replace("%DATE_FILTERS%", df.toString());

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, holdingId);
            if (from != null) ps.setDate(idx++, Date.valueOf(from));
            if (to != null) ps.setDate(idx++, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                List<ChartPoint> list = new ArrayList<>();
                while (rs.next()) {
                    ChartPoint v = ChartPoint.builder().id(rs.getLong("id")).date(rs.getDate(Constants.DB.AccountHoldingValuesDaily.COL_DATE).toLocalDate()).currentDayValue(rs.getBigDecimal(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE)).invested(rs.getBigDecimal(Constants.DB.Aliases.COL_INVESTED)).build();
                    list.add(v);
                }
                return list;
            }
        }
    }

    public List<ChartPoint> listForAccount(long accountId, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SQL.SELECT).append(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID).append(", ").append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(", SUM(").append(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE).append(") AS ").append(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE).append(Constants.SQL.FROM).append(Constants.DB.AccountHoldingValuesDaily.TABLE).append(Constants.SQL.WHERE).append(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID).append(Constants.SQL.EQ_PARAM);
        if (from != null)
            sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" >= ?");
        if (to != null)
            sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" <= ?");
        sb.append(" GROUP BY ").append(String.join(", ", Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID, Constants.DB.AccountHoldingValuesDaily.COL_DATE)).append(" ORDER BY ").append(Constants.DB.AccountHoldingValuesDaily.COL_DATE);

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            ps.setLong(idx++, accountId);
            if (from != null) ps.setDate(idx++, Date.valueOf(from));
            if (to != null) ps.setDate(idx++, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                List<ChartPoint> list = new ArrayList<>();
                while (rs.next()) {
                    ChartPoint v = ChartPoint.builder().id(rs.getLong(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID)).date(rs.getDate(Constants.DB.AccountHoldingValuesDaily.COL_DATE).toLocalDate()).currentDayValue(rs.getBigDecimal(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE)).build();
                    list.add(v);
                }
                return list;
            }
        }
    }
}
