package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;

import java.util.List;

public class IncomeTagService extends Service<IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
    }
}
