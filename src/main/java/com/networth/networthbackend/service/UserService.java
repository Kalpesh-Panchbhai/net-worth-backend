package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.UserRest;

public class UserService extends Service<UserRest, UserEntity> {

    public UserService() {
        super(new UserRepository());
    }
}