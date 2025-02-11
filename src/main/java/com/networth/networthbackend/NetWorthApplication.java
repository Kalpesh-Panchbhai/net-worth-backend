package com.networth.networthbackend;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NetWorthApplication {

    public static void main(String[] args) {

        String tableName = "networth";
        List<String> fieldName = Arrays.asList("account_id");
        String query = "select * from " + tableName + " where (" + String.join(" = ? OR ", fieldName) + " = ?)";
        System.out.println(query);
//        Connection connection = null;
//        try {
//            String userName = "avnadmin";
//            String password = "AVNS_rUprsHw62c14G_fc0fx";
//
//            String url = "jdbc:mysql://net-worth-net-worth.i.aivencloud.com:18558/net_worth?user=" + userName + "&password=" + password + "&useUnicode=true&characterEncoding=UTF-8";
//            Class.forName("com.mysql.jdbc.Driver").newInstance();
//            connection = DriverManager.getConnection(url);
//            System.out.println("Database connection established");
//            String insertQuery = "INSERT INTO user (id) VALUES (?)";
//            PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
//            preparedStatement.setString(1, "1");
//            int affectedRows = preparedStatement.executeUpdate();
//            if (affectedRows > 0) {
//                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
//                    if (generatedKeys.next()) {
//                        System.out.println(generatedKeys.getInt(1));
//                    }
//                }
//            }

//            AccountRepository accountRepository = new AccountRepository();
//            AccountEntity accountEntity = new AccountEntity();
//            accountEntity.setName("test");
//            accountEntity.setActive(true);
//            accountEntity.setType("Broker");
//            accountEntity.setCurrency("INR");
//            accountEntity.setUserId("U1");
//            accountRepository.save(accountEntity);
//        } catch (Exception e) {
//            System.err.println("Cannot connect to database server");
//            System.err.println(e.getMessage());
//            e.printStackTrace();
//        } finally {
//            if (connection != null) {
//                try {
//                    connection.close();
//                    System.out.println("Database Connection Terminated");
//                } catch (Exception e) {
//                }
//            }
    }
}
