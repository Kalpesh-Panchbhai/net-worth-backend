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
            public static final String PUT = "PUT";
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
            public static final int INTERNAL_SERVER_ERROR = 500;
        }
    }

    @UtilityClass
    public static class Cors {
        public static final String ALLOWED_METHODS = "POST,GET,PUT,DELETE"; // OPTIONS removed by design
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
    }

    @UtilityClass
    public static class Entities {
        public static final String USERS = "users";
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
    }
}
