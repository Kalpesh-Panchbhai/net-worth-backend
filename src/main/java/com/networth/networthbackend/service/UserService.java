package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.UserRepository;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.UserRest;

public class UserService extends Service<UserRest, UserEntity> {

    public UserService() {
        super(new UserRepository());
    }

    @Override
    public UserEntity convertRequestToEntity(UserRest userRest) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userRest.getId());
        return userEntity;
    }
}