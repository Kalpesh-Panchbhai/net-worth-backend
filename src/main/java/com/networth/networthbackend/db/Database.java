package com.networth.networthbackend.db;

import lombok.experimental.UtilityClass;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import com.networth.networthbackend.common.Constants;

@UtilityClass
public class Database {

    public static Connection getConnection() throws SQLException {
        String url = System.getenv(Constants.Env.DB_URL);
        String user = System.getenv(Constants.Env.DB_USERNAME);
        String password = System.getenv(Constants.Env.DB_PASSWORD);

        if (url == null || url.isBlank()) {
            throw new SQLException(Constants.Messages.DB_URL_NOT_SET);
        }
        if (user == null) user = "";
        if (password == null) password = "";

        try {
            // Ensure driver is loaded in some environments
            Class.forName(Constants.DB.Driver.MYSQL);
        } catch (ClassNotFoundException ignored) {
        }

        return DriverManager.getConnection(url, user, password);
    }
}
