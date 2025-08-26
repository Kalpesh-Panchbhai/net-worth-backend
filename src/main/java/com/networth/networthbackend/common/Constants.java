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
        @UtilityClass
        public static class Method {
            public static final String GET = "GET";
            public static final String POST = "POST";
            public static final String PATCH = "PATCH";
            public static final String DELETE = "DELETE";
        }
        public static final String PATH_SEPARATOR = "/";
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
                  <meta charset=\"utf-8\"/>
                  <title>API Docs</title>
                  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui.css\" />
                </head>
                <body>
                  <div id=\"swagger-ui\"></div>
                  <script src=\"https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.17.14/swagger-ui-bundle.js\"></script>
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
        public static final String ENTITY = "entity";
        public static final String ACTION = "action";
        public static final String USER_ID = "userId";
        public static final String NAME = "name";
        public static final String DEFAULT = "default";
        public static final String INCOME_SOURCE_ID = "incomeSourceId";
        public static final String INCOME_TAG_ID = "incomeTagId";
        public static final String NET_AMOUNT = "netAmount";
        public static final String TAX_PAID = "taxPaid";
        public static final String CURRENCY = "currency";
        public static final String CREDITED_DATE = "creditedDate";
    }

    @UtilityClass
    public static class Entities {
        public static final String USERS = "users";
        public static final String INCOME_TAGS = "income-tags";
        public static final String INCOME_SOURCES = "income-sources";
        public static final String INCOMES = "incomes";
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
            public static final String COL_USER_ID = "user_id";
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
        public static final String USER_ID_REQUIRED = "userId is required in JSON body or as query param";
        public static final String USER_NOT_FOUND = "User not found";
        public static final String USER_FETCH_AFTER_UPSERT_FAILED = "Failed to fetch user after upsert";
        public static final String PATH_ID_REQUIRED = "Path parameter 'id' is required";
        public static final String REQUEST_BODY_REQUIRED = "Request body is required";

        // Income Tags
        public static final String INCOME_TAG_USER_ID_REQUIRED = "userId is required to create income tag";
        public static final String INCOME_TAG_NAME_REQUIRED = "name is required to create income tag";
        public static final String INCOME_TAG_NAME_DUPLICATE = "Income tag with the same name already exists for this user";
        public static final String INCOME_TAG_NOT_FOUND = "Income tag not found";

        // Income Sources
        public static final String INCOME_SOURCE_USER_ID_REQUIRED = "userId is required to create income source";
        public static final String INCOME_SOURCE_NAME_REQUIRED = "name is required to create income source";
        public static final String INCOME_SOURCE_NAME_DUPLICATE = "Income source with the same name already exists for this user";
        public static final String INCOME_SOURCE_NOT_FOUND = "Income source not found";

        // Incomes
        public static final String INCOME_USER_ID_REQUIRED = "userId is required to create income";
        public static final String INCOME_SOURCE_ID_REQUIRED = "incomeSourceId is required";
        public static final String INCOME_TAG_ID_REQUIRED = "incomeTagId is required";
        public static final String INCOME_NET_AMOUNT_REQUIRED = "netAmount is required";
        public static final String INCOME_CURRENCY_REQUIRED = "currency is required";
        public static final String INCOME_CREDITED_DATE_REQUIRED = "creditedDate is required (UTC ISO 8601)";
        public static final String INCOME_NOT_FOUND = "Income not found";
        public static final String INCOME_UPDATE_NO_FIELDS = "At least one editable field must be provided";
    }

    @UtilityClass
    public static class Logs {
        // Generic/system
        public static final String UNHANDLED_ERROR = "Unhandled error in handler";

        // Router
        public static final String ROUTER_RECEIVED = "Router received: method={}, path={}";
        public static final String DISPATCH_ENTITY_ACTION = "Dispatching to entity='{}', action='{}'";

        // Users
        public static final String USER_ENSURE = "Ensuring user exists for userId={}";
        public static final String USER_ENSURED = "User ensured: id={}, userId={}";
        public static final String USER_UPSERT_AFFECTED = "Upsert users affected rows: {} for userId={} ";
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
        public static final String INCOME_UPDATE_AFFECTED = "Update incomes affected rows: {} for id={}";
    }
}
