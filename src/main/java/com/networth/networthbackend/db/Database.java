package com.networth.networthbackend.db;

import com.networth.networthbackend.common.Constants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Pooled JDBC access.
 * <p>
 * Every repository call used to open a brand-new MySQL connection (TCP + TLS + auth handshake),
 * which dominated API latency because a single page load issues many queries. The pool is created
 * once per JVM (a warm Lambda container or the long-running HTTP server) and reused.
 */
@Slf4j
@UtilityClass
public class Database {

    private static volatile HikariDataSource dataSource;

    public static Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    /** Closes the pool. Only useful for local runs / tests; Lambda containers are torn down for us. */
    public static void shutdown() {
        HikariDataSource ds = dataSource;
        dataSource = null;
        if (ds != null) ds.close();
    }

    private static DataSource dataSource() throws SQLException {
        HikariDataSource ds = dataSource;
        if (ds != null) return ds;
        synchronized (Database.class) {
            if (dataSource == null) dataSource = build();
            return dataSource;
        }
    }

    private static HikariDataSource build() throws SQLException {
        String url = System.getenv(Constants.Env.DB_URL);
        if (url == null || url.isBlank()) {
            throw new SQLException(Constants.Messages.DB_URL_NOT_SET);
        }
        String user = System.getenv(Constants.Env.DB_USERNAME);
        String password = System.getenv(Constants.Env.DB_PASSWORD);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user == null ? "" : user);
        cfg.setPassword(password == null ? "" : password);
        cfg.setDriverClassName(Constants.DB.Driver.MYSQL);
        cfg.setPoolName(Constants.DB.Pool.NAME);

        cfg.setMaximumPoolSize(intEnv(Constants.Env.DB_POOL_MAX_SIZE, Constants.DB.Pool.DEFAULT_MAX_SIZE));
        cfg.setMinimumIdle(intEnv(Constants.Env.DB_POOL_MIN_IDLE, Constants.DB.Pool.DEFAULT_MIN_IDLE));
        cfg.setConnectionTimeout(Constants.DB.Pool.CONNECTION_TIMEOUT_MS);
        cfg.setValidationTimeout(Constants.DB.Pool.VALIDATION_TIMEOUT_MS);
        cfg.setIdleTimeout(Constants.DB.Pool.IDLE_TIMEOUT_MS);
        // Stay comfortably below typical RDS/proxy idle kill windows so we never hand out a dead socket.
        cfg.setMaxLifetime(Constants.DB.Pool.MAX_LIFETIME_MS);
        cfg.setKeepaliveTime(Constants.DB.Pool.KEEPALIVE_MS);

        // Driver-level socket timeouts. Hikari's connectionTimeout only bounds how long a caller
        // waits for a connection FROM the pool; Connector/J's own socketTimeout defaults to 0,
        // i.e. infinite, so a silently dropped connection would pin a worker thread (or the whole
        // Lambda invocation) until the platform killed it.
        cfg.addDataSourceProperty("connectTimeout", String.valueOf(Constants.DB.Pool.SOCKET_CONNECT_TIMEOUT_MS));
        cfg.addDataSourceProperty("socketTimeout", String.valueOf(Constants.DB.Pool.SOCKET_READ_TIMEOUT_MS));

        // Server-side prepared statements + statement cache: the same handful of queries run on every request.
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "4096");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");
        cfg.addDataSourceProperty("useLocalSessionState", "true");
        cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
        cfg.addDataSourceProperty("cacheServerConfiguration", "true");
        cfg.addDataSourceProperty("elideSetAutoCommits", "true");
        cfg.addDataSourceProperty("maintainTimeStats", "false");

        HikariDataSource built = new HikariDataSource(cfg);
        log.info("Initialised JDBC pool '{}' (max={}, minIdle={})", cfg.getPoolName(), cfg.getMaximumPoolSize(), cfg.getMinimumIdle());
        return built;
    }

    private static int intEnv(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            // A zero or negative pool size makes HikariConfig throw an unchecked exception out of
            // every getConnection() call, so a typo would take the whole API down.
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
            // fall through to the warning below
        }
        log.warn("Ignoring invalid {}={}, using {}", name, raw, fallback);
        return fallback;
    }
}
