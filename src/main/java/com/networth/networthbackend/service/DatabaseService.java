package com.networth.networthbackend.service;

import java.sql.Connection;
import java.sql.DriverManager;

public class DatabaseService {

    private static final DatabaseService databaseService = new DatabaseService();
    String userName = "avnadmin";
    String password = "AVNS_rKmg4JyzsWkZNcy9jfx";
    String url = "jdbc:mysql://net-worth-mysql-net-worth.h.aivencloud.com:18558/net_worth?user=" + userName + "&password=" + password + "&useUnicode=true&characterEncoding=UTF-8";
    private Connection conn = null;

    private DatabaseService() {
    }

    public static DatabaseService getInstance() {
        return databaseService;
    }

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
        } catch (Exception e) {
            System.err.println("Cannot connect to database server");
            System.err.println(e.getMessage());
        }
    }

    public void disconnect() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("Database Connection Terminated");
            } catch (Exception e) {
            }
        }
    }
}