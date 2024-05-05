package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.UserEntity;

public class UserRepository extends Repository<UserEntity> {

    public UserRepository() {
        tableName = "user";
    }
}
