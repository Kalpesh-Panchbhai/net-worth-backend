package com.networth.networthbackend.service;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseService {

    String userName = System.getenv("DB_USERNAME");
    String password = System.getenv("DB_PASSWORD");
    String url = System.getenv("DB_URL") + "?user=" + userName + "&password=" + password + "&useUnicode=true&characterEncoding=UTF-8";
    private Connection conn = null;

    public Connection getConnection() {
        if (conn == null) {
            connect();
        }
        return conn;
    }

    private void connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(url);
//            System.out.println("Connected to database");
        } catch (Exception e) {
            System.err.println("Cannot connect to database server");
            System.err.println(e.getMessage());
        }
    }

    public void disconnect() {
        if (conn != null) {
            try {
                conn.close();
//                System.out.println("Database Connection Terminated");
            } catch (Exception e) {
            }
        }
    }
}