package com.networth.networthbackend.Repository;

import com.amazonaws.services.dynamodbv2.model.ScanResult;

public interface RepositoryImpl<T> {

    void save(T data);

    void update(T data);

    void delete(T data);

    T getByPrimaryField(String id, Class<T> type);

    ScanResult getBySecondaryField(String fieldName, String fieldValue);
}
