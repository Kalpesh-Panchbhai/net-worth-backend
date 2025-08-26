package com.networth.networthbackend.incometags;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.List;

@Slf4j
public class IncomeTagRepository extends JdbcRepository<IncomeTag> {

    private static final RowMapper<IncomeTag> MAPPER = rs -> IncomeTag.builder()
            .id(rs.getLong(Constants.DB.IncomeTags.COL_ID))
            .userId(rs.getLong(Constants.DB.IncomeTags.COL_USER_ID))
            .name(rs.getString(Constants.DB.IncomeTags.COL_NAME))
            .isDefault(rs.getBoolean(Constants.DB.IncomeTags.COL_IS_DEFAULT))
            .build();

    public List<IncomeTag> listByUserId(long userId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                        Constants.DB.IncomeTags.COL_ID,
                        Constants.DB.IncomeTags.COL_USER_ID,
                        Constants.DB.IncomeTags.COL_NAME,
                        Constants.DB.IncomeTags.COL_IS_DEFAULT)
                + Constants.SQL.FROM + Constants.DB.IncomeTags.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM;
        return queryList(sql, ps -> ps.setLong(1, userId), MAPPER);
    }
}
