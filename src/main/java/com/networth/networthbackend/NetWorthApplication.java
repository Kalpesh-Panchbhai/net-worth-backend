package com.networth.networthbackend;


import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;

import java.util.Optional;

public class NetWorthApplication {

    public static void main(String[] args) {

//        Connection conn = null;
        try {
//            String userName = "avnadmin";
//            String password = "AVNS_rUprsHw62c14G_fc0fx";
//
//            String url = "jdbc:mysql://net-worth-net-worth.i.aivencloud.com:18558/net_worth?user=" + userName + "&password=" + password + "&useUnicode=true&characterEncoding=UTF-8";
//            Class.forName("com.mysql.jdbc.Driver").newInstance();
//            conn = DriverManager.getConnection(url);
//            System.out.println("Database connection established");
//            String userId = "AA";
//            String query = "select * from account where user_id = ?";
//            PreparedStatement pstmt = conn.prepareStatement(query);
//            pstmt.setString(1, userId);
//            ResultSet rs = pstmt.executeQuery();
//
//            // Process ResultSet
//            while (rs.next()) {
//                int id = rs.getInt("id");
//                String name = rs.getString("name");
//                String email = rs.getString("type");
//                Boolean active = rs.getBoolean("is_active");
//                String currency = rs.getString("currency");
//                System.out.println(id + " " + name + " " + email + " " + active + " " + currency);
//
//            }


            UserRepository userRepository = new UserRepository();
            Optional<UserEntity> userEntity = userRepository.getByPrimaryField("AA");
            userEntity.ifPresent(System.out::println);
        } catch (Exception e) {
            System.err.println("Cannot connect to database server");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}
