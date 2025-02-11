package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;

import java.util.List;

public class UserService extends Service<UserEntity> {

    public UserService() {
        super(new UserRepository());
        secondaryFieldNameList = List.of("user_id");
    }
}