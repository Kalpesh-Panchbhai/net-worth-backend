package com.networth.networthbackend.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    @UtilityClass
    public static class Env {
        public static final String DB_URL = "DB_URL";
        public static final String DB_USERNAME = "DB_USERNAME";
        public static final String DB_PASSWORD = "DB_PASSWORD";
    }

    @UtilityClass
    public static class Http {
        public static final String PATH_SEPARATOR = "/";

        @UtilityClass
        public static class Method {
            public static final String GET = "GET";
            public static final String POST = "POST";
            public static final String PATCH = "PATCH";
            public static final String DELETE = "DELETE";
        }

        @UtilityClass
        public static class Header {
            public static final String CONTENT_TYPE = "Content-Type";
            public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
            public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
            public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
        }

        @UtilityClass
        public static class ContentType {
            public static final String APPLICATION_JSON = "application/json";
            public static final String TEXT_HTML_UTF8 = "text/html; charset=UTF-8";
        }

        @UtilityClass
        public static class Status {
            public static final int OK = 200;
            public static final int CREATED = 201;
            public static final int NO_CONTENT = 204;
            public static final int BAD_REQUEST = 400;
            public static final int NOT_FOUND = 404;
            public static final int CONFLICT = 409;
            public static final int INTERNAL_SERVER_ERROR = 500;
        }
    }

    @UtilityClass
    public static class Cors {
        public static final String ALLOWED_METHODS = "POST,GET,PATCH,DELETE"; // OPTIONS removed by design; PUT removed
        public static final String ALLOWED_HEADERS = "*";
        public static final String ALLOW_ORIGIN = "*";
    }

    @UtilityClass
    public static class Api {
        public static final String ERROR_FIELD = "error";
        public static final String EMPTY_JSON = "{}";
        public static final String EMPTY_BODY = "";
    }

    @UtilityClass
    public static class SQL {
        public static final String DELETE_FROM = "DELETE FROM ";
        public static final String SPACE = " ";
        public static final String COMMA_SPACE = ", ";
        public static final String OPEN_PAREN = "(";
        public static final String CLOSE_PAREN = ")";

        public static final String SELECT = "SELECT ";
        public static final String FROM = " FROM ";
        public static final String WHERE = " WHERE ";
        public static final String UPDATE = "UPDATE ";
        public static final String SET = " SET ";
        public static final String AND = " AND ";
        public static final String LIMIT_1 = " LIMIT 1";
        public static final String INSERT_INTO = "INSERT INTO ";
        public static final String INSERT_IGNORE_INTO = "INSERT IGNORE INTO ";
        public static final String VALUES = " VALUES ";
        public static final String ON_DUPLICATE_KEY_UPDATE = " ON DUPLICATE KEY UPDATE ";
        public static final String EQ_VALUES_OPEN = " = VALUES(";
        public static final String CLOSE = ")";
        public static final String PARAM = "?";
        public static final String EQ_PARAM = " = ?";
    }

    @UtilityClass
    public static class Path {
        public static final String ID = "id";
    }

    @UtilityClass
    public static class Docs {
        public static final String SWAGGER_UI_HTML = """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <title>API Docs</title>
                  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui.css" />
                </head>
                <body>
                  <div id="swagger-ui"></div>
                  <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui-bundle.js"></script>
                  <script>
                    window.ui = SwaggerUIBundle({
                      url: '/openapi.json',
                      dom_id: '#swagger-ui'
                    });
                  </script>
                </body>
                </html>
                """;
    }

    @UtilityClass
    public static class ErrorCodes {
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String NOT_FOUND = "NOT_FOUND";
        public static final String CONFLICT = "CONFLICT";
        public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
        public static final String ENTITY_NOT_SUPPORTED = "ENTITY_NOT_SUPPORTED";
        public static final String ACTION_REQUIRED = "ACTION_REQUIRED";
        public static final String ACTION_NOT_SUPPORTED = "ACTION_NOT_SUPPORTED";
    }

    @UtilityClass
    public static class Query {
        public static final String USER_ID = "userId";
        public static final String EXTERNAL_USER_ID = "externalUserId";
        public static final String NAME = "name";
        public static final String DEFAULT = "default";
        public static final String IS_DEFAULT = "isDefault";
        public static final String INCOME_SOURCE_ID = "incomeSourceId";
        public static final String INCOME_TAG_ID = "incomeTagId";
        public static final String NET_AMOUNT = "netAmount";
        public static final String TAX_PAID = "taxPaid";
        public static final String CURRENCY = "currency";
        public static final String CREDITED_DATE = "creditedDate";
        public static final String TYPE = "type";
        public static final String IS_ACTIVE = "isActive";
        public static final String ACCOUNT_ID = "accountId";
        public static final String HOLDING_ID = "holdingId";
        public static final String TXN_DATE = "txnDate";
        public static final String INVESTED = "invested";
        public static final String VALUE = "value";
        public static final String SYMBOL = "symbol";
        public static final String UNITS = "units";
        public static final String WATCHLIST_ID = "watchlistId";
    }

    @UtilityClass
    public static class Entities {
        public static final String USERS = "users";
        public static final String INCOME_TAGS = "income-tags";
        public static final String INCOME_SOURCES = "income-sources";
        public static final String INCOMES = "incomes";
        public static final String WATCHLISTS = "watchlists";
        public static final String ACCOUNTS = "accounts";
        public static final String HOLDINGS = "holdings";
        public static final String WATCHLIST_ACCOUNTS = "watchlist-accounts";
        public static final String ACCOUNT_WATCHLISTS = "account-watchlists";
        public static final String TRANSACTIONS = "transactions";
    }

    @UtilityClass
    public static class Accounts {
        public static final String TYPE_BROKER = "BROKER";
        public static final String TYPE_SAVINGS = "SAVINGS";
        public static final String TYPE_CREDIT_CARD = "CREDIT_CARD";
        public static final String TYPE_LOAN = "LOAN";
        public static final String TYPE_OTHER = "OTHER";

        public static final String AGG_HOLDING_NAME = "Account Aggregate";
        public static final String AGG_HOLDING_SYMBOL = "__ACCT__";
    }

    @UtilityClass
    public static class Actions {
        public static final String CREATE = "create";
        public static final String GET = "get";
        public static final String UPDATE = "update";
        public static final String DELETE = "delete";
    }

    @UtilityClass
    public static class DB {
        @UtilityClass
        public static class Users {
            public static final String TABLE = "users";
            public static final String COL_ID = "id";
            public static final String COL_EXTERNAL_USER_ID = "external_user_id";
        }

        @UtilityClass
        public static class Driver {
            public static final String MYSQL = "com.mysql.cj.jdbc.Driver";
        }

        @UtilityClass
        public static class IncomeTags {
            public static final String TABLE = "income_tags";
            public static final String COL_ID = "id";
            public static final String COL_USER_ID = "user_id";
            public static final String COL_NAME = "name";
            public static final String COL_IS_DEFAULT = "is_default";
        }

        @UtilityClass
        public static class IncomeSources {
            public static final String TABLE = "income_sources";
            public static final String COL_ID = "id";
            public static final String COL_USER_ID = "user_id";
            public static final String COL_NAME = "name";
            public static final String COL_IS_DEFAULT = "is_default";
        }

        @UtilityClass
        public static class Incomes {
            public static final String TABLE = "incomes";
            public static final String COL_ID = "id";
            public static final String COL_USER_ID = "user_id";
            public static final String COL_INCOME_SOURCE_ID = "income_source_id";
            public static final String COL_INCOME_TAG_ID = "income_tag_id";
            public static final String COL_NET_AMOUNT = "net_amount";
            public static final String COL_TAX_PAID = "tax_paid";
            public static final String COL_CURRENCY = "currency";
            public static final String COL_CREDITED_DATE = "credited_date";
        }

        @UtilityClass
        public static class Watchlists {
            public static final String TABLE = "watchlists";
            public static final String COL_ID = "id";
            public static final String COL_USER_ID = "user_id";
            public static final String COL_NAME = "name";

            public static final String SQL_LIST_SUMMARIES_BY_USER_ID = """
                    WITH
                      wls AS (
                        SELECT w.id
                        FROM watchlists w
                        WHERE w.user_id = ?
                      ),
                      wh AS (
                        SELECT h.id AS holding_id
                        FROM wls
                        JOIN watchlist_accounts wa ON wa.watchlist_id = wls.id
                        JOIN accounts a          ON a.id = wa.account_id
                        JOIN holdings h          ON h.account_id = a.id
                      ),
                      hv AS (
                        SELECT ahvd.holding_id,
                               SUM(CASE WHEN ahvd.date = CURRENT_DATE THEN ahvd.current_day_value ELSE 0 END)                AS curr_val,
                               SUM(CASE WHEN ahvd.date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY) THEN ahvd.current_day_value ELSE 0 END) AS prev_val
                        FROM account_holding_values_daily ahvd
                        JOIN (SELECT DISTINCT holding_id FROM wh) d ON d.holding_id = ahvd.holding_id
                        WHERE ahvd.date IN (CURRENT_DATE, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY))
                        GROUP BY ahvd.holding_id
                      ),
                      li AS (
                        SELECT t.holding_id, t.invested
                        FROM (
                          SELECT ahvd.holding_id, ahvd.invested,
                                 ROW_NUMBER() OVER (PARTITION BY ahvd.holding_id ORDER BY ahvd.date DESC) AS rn
                          FROM account_holding_values_daily ahvd
                          JOIN (SELECT DISTINCT holding_id FROM wh) d ON d.holding_id = ahvd.holding_id
                        ) t
                        WHERE t.rn = 1
                      )
                    SELECT
                      w.id,
                      w.user_id,
                      w.name,
                      COALESCE(SUM(hv.curr_val), 0) AS current_day_value,
                      COALESCE(SUM(hv.prev_val), 0) AS previous_day_value,
                      COALESCE(SUM(li.invested), 0) AS invested
                    FROM watchlists w
                    LEFT JOIN watchlist_accounts wa ON wa.watchlist_id = w.id
                    LEFT JOIN accounts a            ON a.id = wa.account_id
                    LEFT JOIN holdings h            ON h.account_id = a.id
                    LEFT JOIN hv                    ON hv.holding_id = h.id
                    LEFT JOIN li                    ON li.holding_id = h.id
                    WHERE w.user_id = ?
                    GROUP BY w.id, w.user_id, w.name
                    ORDER BY w.id
                    """;

            public static final String SQL_LIST_SUMMARIES_BY_ACCOUNT_ID = """
                    SELECT
                      w.id,
                      w.name,
                      w.user_id,
                      COALESCE(SUM(CASE WHEN ahvd.date = CURRENT_DATE                 THEN ahvd.current_day_value END), 0) AS current_day_value,
                      COALESCE(SUM(CASE WHEN ahvd.date = DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY) THEN ahvd.current_day_value END), 0) AS previous_day_value,
                      COALESCE(SUM(CASE WHEN ahvd.date = CURRENT_DATE                 THEN ahvd.invested            END), 0) AS invested
                    FROM watchlist_accounts wa
                    JOIN watchlists w
                      ON w.id = wa.watchlist_id
                    LEFT JOIN account_holding_values_daily ahvd
                      ON ahvd.account_id = wa.account_id
                     AND ahvd.date IN (CURRENT_DATE, DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY))
                    WHERE wa.account_id = ?
                    GROUP BY w.id, w.name, w.user_id
                    ORDER BY w.id
                    """;
        }

        @UtilityClass
        public static class Accounts {
            public static final String TABLE = "accounts";
            public static final String COL_ID = "id";
            public static final String COL_USER_ID = "user_id";
            public static final String COL_NAME = "name";
            public static final String COL_TYPE = "type";
            public static final String COL_IS_ACTIVE = "is_active";
            public static final String COL_CURRENCY = "currency";
            public static final String SQL_LIST_SUMMARIES_BY_USER_ID = """
                    WITH
                      my_accounts AS (
                        SELECT a.*
                        FROM accounts a
                        JOIN users u ON u.id = a.user_id
                        WHERE u.id = ?
                      ),
                      curr AS (
                        SELECT s.account_id, SUM(s.current_day_value) AS current_day_value
                        FROM account_holding_values_daily s
                        JOIN my_accounts a ON a.id = s.account_id
                        WHERE s.date = CURRENT_DATE
                        GROUP BY s.account_id
                      ),
                      prev AS (
                        SELECT s.account_id, SUM(s.current_day_value) AS previous_day_value
                        FROM account_holding_values_daily s
                        JOIN my_accounts a ON a.id = s.account_id
                        WHERE s.date = CURRENT_DATE - INTERVAL 1 DAY
                        GROUP BY s.account_id
                      ),
                      max_dates AS (
                        SELECT s.account_id, MAX(s.date) AS max_date
                        FROM account_holding_values_daily s
                        JOIN my_accounts a ON a.id = s.account_id
                        GROUP BY s.account_id
                      ),
                      latest AS (
                        SELECT s.account_id, SUM(s.invested) AS invested
                        FROM account_holding_values_daily s
                        JOIN max_dates md
                          ON md.account_id = s.account_id
                         AND md.max_date  = s.date
                        GROUP BY s.account_id
                      )
                    SELECT
                      a.id,
                      a.user_id,
                      a.name,
                      a.type,
                      a.is_active,
                      a.currency,
                      COALESCE(c.current_day_value, CAST(0 AS DECIMAL(10,4))) AS current_day_value,
                      COALESCE(p.previous_day_value, CAST(0 AS DECIMAL(10,4))) AS previous_day_value,
                      COALESCE(l.invested,    CAST(0 AS DECIMAL(10,4))) AS invested
                    FROM my_accounts a
                    LEFT JOIN curr   c ON c.account_id = a.id
                    LEFT JOIN prev   p ON p.account_id = a.id
                    LEFT JOIN latest l ON l.account_id = a.id
                    ORDER BY a.id
                    """;
            public static final String SQL_LIST_SUMMARIES_BY_WATCHLIST_ID = """
                    WITH
                      watch_accounts AS (
                        SELECT a.*
                        FROM accounts a
                        JOIN watchlist_accounts wa
                          ON wa.account_id = a.id
                        WHERE wa.watchlist_id = ?
                      ),
                      daily_sums AS (
                        SELECT avd.account_id,
                               avd.date,
                               SUM(avd.invested)          AS invested_sum,
                               SUM(avd.current_day_value) AS value_sum
                        FROM account_holding_values_daily avd
                        WHERE avd.holding_id <> 0
                          AND avd.account_id IN (SELECT id FROM watch_accounts)
                        GROUP BY avd.account_id, avd.date
                      ),
                      ranked AS (
                        SELECT ds.*,
                               ROW_NUMBER() OVER (PARTITION BY ds.account_id ORDER BY ds.date DESC) AS rn
                        FROM daily_sums ds
                      )
                    SELECT
                      wa.id,
                      wa.user_id,
                      wa.name,
                      wa.type,
                      wa.is_active,
                      wa.currency,
                      COALESCE(l.value_sum, 0)    AS current_day_value,
                      COALESCE(p.value_sum, 0)    AS previous_day_value,
                      COALESCE(l.invested_sum, 0) AS invested
                    FROM watch_accounts wa
                    LEFT JOIN ranked l
                      ON l.account_id = wa.id AND l.rn = 1
                    LEFT JOIN ranked p
                      ON p.account_id = wa.id AND p.rn = 2
                    ORDER BY wa.id
                    """;
        }

        @UtilityClass
        public static class Holdings {
            public static final String TABLE = "holdings";
            public static final String COL_ID = "id";
            public static final String COL_ACCOUNT_ID = "account_id";
            public static final String COL_NAME = "name";
            public static final String COL_SYMBOL = "symbol";
            public static final String SQL_LIST_SUMMARIES_BY_ACCOUNT_ID = """
                    WITH
                      v_today AS (
                        SELECT account_id, holding_id, invested, current_day_value
                        FROM account_holding_values_daily
                        WHERE account_id = ? AND date = CURRENT_DATE
                      ),
                      v_yday AS (
                        SELECT account_id, holding_id, current_day_value
                        FROM account_holding_values_daily
                        WHERE account_id = ? AND date = CURRENT_DATE - INTERVAL 1 DAY
                      ),
                      v_latest AS (
                        SELECT account_id, holding_id, value AS units
                        FROM (
                          SELECT account_id, holding_id, value,
                                 ROW_NUMBER() OVER (PARTITION BY account_id, holding_id ORDER BY txn_date DESC) AS rn
                          FROM transactions
                          WHERE account_id = ?
                        ) x
                        WHERE rn = 1
                      )
                    SELECT
                      h.id,
                      h.account_id,
                      h.name,
                      h.symbol,
                      COALESCE(v_latest.units, CAST(0 AS DECIMAL(10,4))) AS units,
                      COALESCE(v_today.current_day_value, CAST(0 AS DECIMAL(10,4))) AS current_day_value,
                      COALESCE(v_yday.current_day_value,  CAST(0 AS DECIMAL(10,4))) AS previous_day_value,
                      COALESCE(v_today.invested,           CAST(0 AS DECIMAL(10,4))) AS invested
                    FROM holdings h
                    LEFT JOIN v_today
                      ON v_today.account_id = h.account_id
                     AND v_today.holding_id = h.id
                    LEFT JOIN v_yday
                      ON v_yday.account_id = h.account_id
                     AND v_yday.holding_id = h.id
                    LEFT JOIN v_latest
                      ON v_latest.account_id = h.account_id
                     AND v_latest.holding_id = h.id
                    WHERE h.account_id = ?
                    ORDER BY h.id
                    """;
        }

        @UtilityClass
        public static class WatchlistAccounts {
            public static final String TABLE = "watchlist_accounts";
            public static final String COL_WATCHLIST_ID = "watchlist_id";
            public static final String COL_ACCOUNT_ID = "account_id";
        }

        @UtilityClass
        public static class AccountHoldingValuesDaily {
            public static final String TABLE = "account_holding_values_daily";
            public static final String COL_ACCOUNT_ID = "account_id";
            public static final String COL_HOLDING_ID = "holding_id";
            public static final String COL_DATE = "date";
            public static final String COL_INVESTED = "invested";
            public static final String COL_CURRENT_DAY_VALUE = "current_day_value";
        }

        @UtilityClass
        public static class Transactions {
            public static final String TABLE = "transactions";
            public static final String COL_ID = "id";
            public static final String COL_ACCOUNT_ID = "account_id";
            public static final String COL_HOLDING_ID = "holding_id";
            public static final String COL_TXN_DATE = "txn_date";
            public static final String COL_INVESTED = "invested";
            public static final String COL_VALUE = "value";
        }

        @UtilityClass
        public static class Aliases {
            public static final String COL_CURRENT_DAY_VALUE = "current_day_value";
            public static final String COL_PREVIOUS_DAY_VALUE = "previous_day_value";
            public static final String COL_INVESTED = "invested";
        }
    }

    @UtilityClass
    public static class Messages {
        // Generic/system
        public static final String INTERNAL_ERROR = "Internal error";
        public static final String SERIALIZATION_ERROR_FMT = "Serialization error: %s";
        public static final String DB_URL_NOT_SET = "DB_URL env var is not set";

        // Routing / actions
        public static final String UNKNOWN_ROUTE = "Unknown route";
        public static final String ENTITY_NOT_SUPPORTED_FMT = "Entity not supported: %s";
        public static final String ACTION_REQUIRED = "Action must be specified";
        public static final String ACTION_COULD_NOT_BE_INFERRED = "Action could not be inferred; specify in path or query param";
        public static final String ACTION_NOT_SUPPORTED_FMT = "Unsupported action for %s: %s";

        // Users
        public static final String EXTERNAL_USER_ID_REQUIRED = "externalUserId is required in JSON body";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String USER_FETCH_AFTER_UPSERT_FAILED = "Failed to fetch user after upsert";
        public static final String PATH_ID_REQUIRED = "Path parameter 'id' is required";
        public static final String REQUEST_BODY_REQUIRED = "Request body is required";

        // Income Tags
        public static final String INCOME_TAG_USER_ID_REQUIRED = "userId is required to create income tag";
        public static final String INCOME_TAG_NAME_REQUIRED = "name is required to create income tag";
        public static final String INCOME_TAG_NAME_DUPLICATE = "Income tag with the same name already exists.";
        public static final String INCOME_TAG_NOT_FOUND = "Income tag not found";

        // Income Sources
        public static final String INCOME_SOURCE_USER_ID_REQUIRED = "userId is required to create income source";
        public static final String INCOME_SOURCE_NAME_REQUIRED = "name is required to create income source";
        public static final String INCOME_SOURCE_NAME_DUPLICATE = "Income source with the same name already exists.";
        public static final String INCOME_SOURCE_NOT_FOUND = "Income source not found";

        // Incomes
        public static final String INCOME_USER_ID_REQUIRED = "userId is required to create income";
        public static final String INCOME_SOURCE_ID_REQUIRED = "incomeSourceId is required";
        public static final String INCOME_TAG_ID_REQUIRED = "incomeTagId is required";
        public static final String INCOME_NET_AMOUNT_REQUIRED = "netAmount is required";
        public static final String INCOME_CURRENCY_REQUIRED = "currency is required";
        public static final String INCOME_CREDITED_DATE_REQUIRED = "creditedDate is required 'YYYY-MM-DD' format";
        public static final String INCOME_DUPLICATE = "Income for the same date, source and tag already exists.";
        public static final String INCOME_NOT_FOUND = "Income not found";
        public static final String INCOME_UPDATE_NO_FIELDS = "At least one editable field must be provided";

        // Watchlists
        public static final String WATCHLIST_USER_ID_REQUIRED = "userId is required to create watchlist";
        public static final String WATCHLIST_NAME_REQUIRED = "name is required to create or update watchlist";
        public static final String WATCHLIST_NAME_DUPLICATE = "Watchlist with the same name already exists.";
        public static final String WATCHLIST_NOT_FOUND = "Watchlist not found";

        // Accounts
        public static final String ACCOUNT_USER_ID_REQUIRED = "userId is required to create account";
        public static final String ACCOUNT_NAME_REQUIRED = "name is required to create account";
        public static final String ACCOUNT_TYPE_REQUIRED = "type is required to create account";
        public static final String ACCOUNT_CURRENCY_REQUIRED = "currency is required to create account";
        public static final String ACCOUNT_NAME_TYPE_DUPLICATE = "Account with the same name and type already exists for this user";
        public static final String ACCOUNT_NOT_FOUND = "Account not found";
        public static final String ACCOUNT_UPDATE_NO_FIELDS = "At least one editable field (name, isActive) must be provided";

        // Holdings
        public static final String HOLDING_ACCOUNT_ID_REQUIRED = "accountId is required to create holding";
        public static final String HOLDING_NAME_REQUIRED = "name is required to create holding";
        public static final String HOLDING_SYMBOL_REQUIRED = "symbol is required to create holding";
        public static final String HOLDING_UNITS_REQUIRED = "units is required to create holding";
        public static final String HOLDING_NOT_FOUND = "Holding not found";
        public static final String HOLDING_SYMBOL_DUPLICATE = "Holding with the same symbol already exists for this account";

        // Watchlist-Accounts
        public static final String WATCHLIST_ID_REQUIRED = "watchlistId is required";
        public static final String WATCHLIST_ACCOUNT_ID_REQUIRED = "accountId is required";

        // Transactions
        public static final String TRANSACTION_FILTER_REQUIRED = "Either accountId or holdingId is required";
        public static final String TRANSACTION_NOT_FOUND = "Transaction not found";
        public static final String TRANSACTION_ACCOUNT_ID_REQUIRED = "accountId is required";
        public static final String TRANSACTION_HOLDING_ID_REQUIRED = "holdingId is required";
        public static final String TRANSACTION_DATE_REQUIRED = "txnDate is required (YYYY-MM-DD)";
        public static final String TRANSACTION_INVESTED_REQUIRED = "invested is required";
        public static final String TRANSACTION_VALUE_REQUIRED = "value is required";
    }

    @UtilityClass
    public static class Logs {
        // Generic/system
        public static final String UNHANDLED_ERROR = "Unhandled error in handler";

        // Router
        public static final String ROUTER_RECEIVED = "Router received: method={}, path={}";
        public static final String DISPATCH_ENTITY_ACTION = "Dispatching to entity='{}', action='{}'";

        // Users
        public static final String USER_ENSURE = "Ensuring user exists for externalUserId={}";
        public static final String USER_ENSURED = "User ensured: id={}, externalUserId={}";
        public static final String USER_UPSERT_AFFECTED = "Upsert users affected rows: {} for externalUserId={} ";
        public static final String USER_DELETE_AFFECTED = "Delete users affected rows: {} for id={}";

        // Income Tags
        public static final String INCOME_TAGS_LIST = "Listing income tags for userId={}";
        public static final String INCOME_TAGS_LIST_COUNT = "Income tags fetched: count={} for userId={}";
        public static final String INCOME_TAG_CREATE = "Creating income tag for userId={}, name={}, default={}";
        public static final String INCOME_TAG_CREATED = "Income tag created: id={}, userId={}, name={}, default={}";
        public static final String INCOME_TAG_INSERT_AFFECTED = "Insert income_tags affected rows: {} for userId={}, name={}";
        public static final String INCOME_TAG_CLEAR_DEFAULTS_AFFECTED = "Cleared defaults affected rows: {} for userId={}";
        public static final String INCOME_TAG_NAME_DUPLICATE = "Income tag name duplicate for userId={}, name={}";
        public static final String INCOME_TAG_SET_DEFAULT = "Setting income tag as default: tagId={}";
        public static final String INCOME_TAG_SET_DEFAULT_AFFECTED = "Set default affected rows: {} for tagId={}";
        public static final String INCOME_TAG_SET_DEFAULT_DONE = "Income tag set default done: tagId={}, userId={}";

        // Income Sources
        public static final String INCOME_SOURCES_LIST = "Listing income sources for userId={}";
        public static final String INCOME_SOURCES_LIST_COUNT = "Income sources fetched: count={} for userId={}";
        public static final String INCOME_SOURCE_CREATE = "Creating income source for userId={}, name={}, default={}";
        public static final String INCOME_SOURCE_CREATED = "Income source created: id={}, userId={}, name={}, default={}";
        public static final String INCOME_SOURCE_INSERT_AFFECTED = "Insert income_sources affected rows: {} for userId={}, name={}";
        public static final String INCOME_SOURCE_CLEAR_DEFAULTS_AFFECTED = "Cleared source defaults affected rows: {} for userId={}";
        public static final String INCOME_SOURCE_NAME_DUPLICATE = "Income source name duplicate for userId={}, name={}";
        public static final String INCOME_SOURCE_SET_DEFAULT = "Setting income source as default: sourceId={}";
        public static final String INCOME_SOURCE_SET_DEFAULT_AFFECTED = "Set source default affected rows: {} for sourceId={}";
        public static final String INCOME_SOURCE_SET_DEFAULT_DONE = "Income source set default done: sourceId={}, userId={}";

        // Incomes
        public static final String INCOMES_LIST = "Listing incomes for userId={}";
        public static final String INCOMES_LIST_COUNT = "Incomes fetched: count={} for userId={}";
        public static final String INCOME_CREATE = "Creating income for userId={}, sourceId={}, tagId={}, amount={}, tax={}, currency={}, creditedDate={}";
        public static final String INCOME_CREATED = "Income created: id={}, userId={}, sourceId={}, tagId={}, amount={}, tax={}, currency={}, creditedDate={}";
        public static final String INCOME_INSERT_AFFECTED = "Insert incomes affected rows: {} for userId={}, sourceId={}, tagId={}";
        public static final String INCOME_DELETE_AFFECTED = "Delete incomes affected rows: {} for id={}";

        // Watchlists
        public static final String WATCHLIST_CREATE = "Creating watchlist for userId={}, name={}";
        public static final String WATCHLIST_CREATED = "Watchlist created: id={}, userId={}, name={}";
        public static final String WATCHLIST_INSERT_AFFECTED = "Insert watchlists affected rows: {} for userId={}, name={}";
        public static final String WATCHLIST_NAME_DUPLICATE = "Watchlist name duplicate for userId={}, name={}";
        public static final String WATCHLIST_UPDATE_NAME = "Updating watchlist name: id={}, newName={}";
        public static final String WATCHLIST_UPDATE_NAME_AFFECTED = "Update watchlist name affected rows: {} for id={}";
        public static final String WATCHLIST_DELETE_AFFECTED = "Delete watchlists affected rows: {} for id={}";
        public static final String WATCHLISTS_LIST = "Listing watchlists for userId={}";
        public static final String WATCHLISTS_LIST_COUNT = "Watchlists fetched: count={} for userId={}";

        // Account-Watchlists
        public static final String ACCOUNT_WATCHLISTS_LIST = "Listing watchlists for accountId={}";
        public static final String ACCOUNT_WATCHLISTS_LIST_COUNT = "Watchlists fetched: count={} for accountId={}";

        // Accounts
        public static final String ACCOUNT_CREATE = "Creating account for userId={}, name={}, type={}, currency={}, isActive={}";
        public static final String ACCOUNT_CREATED = "Account created: id={}, userId={}, name={}, type={}, currency={}, isActive={}";
        public static final String ACCOUNT_INSERT_AFFECTED = "Insert accounts affected rows: {} for userId={}, name={}, type={}";
        public static final String ACCOUNT_NAME_TYPE_DUPLICATE = "Account name+type duplicate for userId={}, name={}, type={}";
        public static final String ACCOUNT_DELETE_AFFECTED = "Delete accounts affected rows: {} for id={}";
        public static final String ACCOUNT_UPDATE = "Updating account id={} with name={}, isActive={}";
        public static final String ACCOUNT_UPDATE_AFFECTED = "Update accounts affected rows: {} for id={}";
        public static final String ACCOUNTS_LIST = "Listing accounts for userId={}";
        public static final String ACCOUNTS_LIST_COUNT = "Accounts fetched: count={} for userId={}";
        public static final String WATCHLIST_ACCOUNTS_LIST = "Listing accounts for watchlistId={}";
        public static final String WATCHLIST_ACCOUNTS_LIST_COUNT = "Accounts fetched: count={} for watchlistId={}";

        // Holdings
        public static final String HOLDING_CREATE = "Creating holding for accountId={}, name={}, symbol={}, units={}";
        public static final String HOLDING_CREATED = "Holding created: id={}, accountId={}, name={}, symbol={}, units={}";
        public static final String HOLDING_INSERT_AFFECTED = "Insert holdings affected rows: {} for accountId={}, name={}, symbol={}";
        public static final String HOLDING_DELETE_AFFECTED = "Delete holdings affected rows: {} for id={}";
        public static final String HOLDINGS_LIST = "Listing holdings for accountId={}";
        public static final String HOLDINGS_LIST_COUNT = "Holdings fetched: count={} for accountId={}";
        public static final String HOLDING_SYMBOL_DUPLICATE = "Holding symbol duplicate for accountId={}, symbol={}";

        // Watchlist-Accounts
        public static final String WATCHLIST_ACCOUNT_LINK = "Linking accountId={} to watchlistId={}";
        public static final String WATCHLIST_ACCOUNT_LINK_AFFECTED = "Insert watchlist_accounts affected rows: {} for watchlistId={}, accountId={}";
        public static final String WATCHLIST_ACCOUNT_UNLINK_AFFECTED = "Delete watchlist_accounts affected rows: {} for watchlistId={}, accountId={}";

        // Transactions
        public static final String TRANSACTIONS_LIST = "Listing transactions for {}={}";
        public static final String TRANSACTIONS_LIST_COUNT = "Transactions fetched: count={} for {}={}";
        public static final String TRANSACTION_DELETE_AFFECTED = "Delete transactions affected rows: {} for id={}";
        public static final String TRANSACTION_CREATE = "Creating transaction: accountId={}, holdingId={}, date={}, invested={}, value={}";
        public static final String TRANSACTION_CREATED = "Transaction created: id={}, accountId={}, holdingId={}, date={}, invested={}, value={}";
    }

}
