package com.networth.networthbackend.charts;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.db.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ChartRepository extends JdbcRepository<ChartPoint> {

    public List<ChartPoint> listForHolding(long holdingId, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SQL.SELECT)
          .append(String.join(", ",
                  Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID,
                  Constants.DB.AccountHoldingValuesDaily.COL_DATE,
                  Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE))
          .append(Constants.SQL.FROM).append(Constants.DB.AccountHoldingValuesDaily.TABLE)
          .append(Constants.SQL.WHERE).append(Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID).append(Constants.SQL.EQ_PARAM);
        if (from != null) sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" >= ?");
        if (to != null) sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" <= ?");
        sb.append(" ORDER BY ").append(Constants.DB.AccountHoldingValuesDaily.COL_DATE);

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            ps.setLong(idx++, holdingId);
            if (from != null) ps.setDate(idx++, Date.valueOf(from));
            if (to != null) ps.setDate(idx++, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                List<ChartPoint> list = new ArrayList<>();
                while (rs.next()) {
                    ChartPoint v = ChartPoint.builder()
                            .id(rs.getLong(Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID))
                            .date(rs.getDate(Constants.DB.AccountHoldingValuesDaily.COL_DATE).toLocalDate())
                            .currentDayValue(rs.getBigDecimal(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE))
                            .build();
                    list.add(v);
                }
                return list;
            }
        }
    }

    public List<ChartPoint> listForAccount(long accountId, LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SQL.SELECT)
          .append(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID)
          .append(", ")
          .append(Constants.DB.AccountHoldingValuesDaily.COL_DATE)
          .append(", SUM(")
          .append(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE)
          .append(") AS ")
          .append(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE)
          .append(Constants.SQL.FROM).append(Constants.DB.AccountHoldingValuesDaily.TABLE)
          .append(Constants.SQL.WHERE).append(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID).append(Constants.SQL.EQ_PARAM);
        if (from != null) sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" >= ?");
        if (to != null) sb.append(Constants.SQL.AND).append(Constants.DB.AccountHoldingValuesDaily.COL_DATE).append(" <= ?");
        sb.append(" GROUP BY ")
          .append(String.join(", ", Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID, Constants.DB.AccountHoldingValuesDaily.COL_DATE))
          .append(" ORDER BY ")
          .append(Constants.DB.AccountHoldingValuesDaily.COL_DATE);

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            ps.setLong(idx++, accountId);
            if (from != null) ps.setDate(idx++, Date.valueOf(from));
            if (to != null) ps.setDate(idx++, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                List<ChartPoint> list = new ArrayList<>();
                while (rs.next()) {
                    ChartPoint v = ChartPoint.builder()
                            .id(rs.getLong(Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID))
                            .date(rs.getDate(Constants.DB.AccountHoldingValuesDaily.COL_DATE).toLocalDate())
                            .currentDayValue(rs.getBigDecimal(Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE))
                            .build();
                    list.add(v);
                }
                return list;
            }
        }
    }
}
