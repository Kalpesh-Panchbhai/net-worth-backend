package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;

public class UserService extends Service<UserEntity> {

    public UserService() {
        super(new UserRepository());
    }
}