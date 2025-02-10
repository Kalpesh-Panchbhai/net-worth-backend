package com.networth.networthbackend;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class NetWorthApplication {

    public static void main(String[] args) {

        Connection conn = null;
        try {
            String userName = "avnadmin";
            String password = "AVNS_rUprsHw62c14G_fc0fx";

            String url = "jdbc:mysql://net-worth-net-worth.i.aivencloud.com:18558/net_worth?user=" + userName + "&password=" + password + "&useUnicode=true&characterEncoding=UTF-8";
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            conn = DriverManager.getConnection(url);
            System.out.println("Database connection established");
            String userId = "AA";
            String query = "delete from user where id = ?";
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setString(1, userId);
            pstmt.execute();
        } catch (Exception e) {
            System.err.println("Cannot connect to database server");
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("Database Connection Terminated");
                } catch (Exception e) {
                }
            }
        }
    }
}
