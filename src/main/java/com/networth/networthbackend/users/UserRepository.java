package com.networth.networthbackend.users;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.common.SqlBuilder;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Optional;

@Slf4j
public class UserRepository extends JdbcRepository<User> {
    private static final String SQL_SELECT_BY_EXTERNAL_USER_ID = SqlBuilder.selectByUnique(Constants.DB.Users.TABLE, new String[]{Constants.DB.Users.COL_ID, Constants.DB.Users.COL_EXTERNAL_USER_ID}, Constants.DB.Users.COL_EXTERNAL_USER_ID);

    private static final String SQL_INSERT_IGNORE = SqlBuilder.insertIgnore(Constants.DB.Users.TABLE, new String[]{Constants.DB.Users.COL_EXTERNAL_USER_ID});

    private static final String SQL_DELETE_BY_ID = Constants.SQL.DELETE_FROM + Constants.DB.Users.TABLE + Constants.SQL.WHERE + Constants.DB.Users.COL_ID + Constants.SQL.EQ_PARAM; // DELETE FROM users WHERE id = ?

    private static final RowMapper<User> MAPPER = rs -> User.builder().id(rs.getLong(Constants.DB.Users.COL_ID)).externalUserId(rs.getString(Constants.DB.Users.COL_EXTERNAL_USER_ID)).build();

    public Optional<User> findByExternalUserId(String externalUserId) throws SQLException {
        return queryOne(SQL_SELECT_BY_EXTERNAL_USER_ID, ps -> ps.setString(1, externalUserId), MAPPER);
    }

    public int deleteById(long id) throws SQLException {
        int updated = execute(SQL_DELETE_BY_ID, ps -> ps.setLong(1, id));
        log.debug(Constants.Logs.USER_DELETE_AFFECTED, updated, id);
        return updated;
    }

    public EnsureUserResult ensure(String externalUserId) throws SQLException {
        int updated = execute(SQL_INSERT_IGNORE, ps -> ps.setString(1, externalUserId));
        log.debug(Constants.Logs.USER_UPSERT_AFFECTED, updated, externalUserId);
        User user = findByExternalUserId(externalUserId).orElseThrow(() -> new SQLException(Constants.Messages.USER_FETCH_AFTER_UPSERT_FAILED));
        boolean created = updated > 0;
        return EnsureUserResult.builder().user(user).created(created).build();
    }
}
