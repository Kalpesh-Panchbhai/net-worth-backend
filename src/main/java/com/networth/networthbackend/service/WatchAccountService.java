package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.WatchAccountRepository;
import com.networth.networthbackend.entity.WatchAccountEntity;

import java.util.List;

public class WatchAccountService extends Service<WatchAccountEntity> {

    public WatchAccountService() {
        super(new WatchAccountRepository());
    }
}
