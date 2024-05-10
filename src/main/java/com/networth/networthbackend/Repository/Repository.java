package com.networth.networthbackend.Repository;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

import java.util.HashMap;
import java.util.Map;

public class Repository<T> implements RepositoryImpl<T> {

    private final AmazonDynamoDB client;
    private final DynamoDBMapper dynamoDBMapper;
    public String tableName;

    public Repository() {
        client = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("dynamodb.ap-south-1.amazonaws.com", "ap-south-1")).withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("AKIAUF72G2OIBNECZK4F", "EXCUuNK4xD14hqxkrpB04l7XI9TR+JvuthBv9+tZ"))).build();
        dynamoDBMapper = new DynamoDBMapper(client);
    }

    @Override
    public void save(T data) {
        dynamoDBMapper.save(data);
    }

    @Override
    public void update(T data) {
        dynamoDBMapper.save(data);
    }

    @Override
    public void delete(T data) {
        dynamoDBMapper.delete(data);
    }

    @Override
    public T getByPrimaryField(String id, Class<T> type) {
        return dynamoDBMapper.load(type, id);
    }

    @Override
    public ScanResult getBySecondaryField(String fieldName, String fieldValue) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(fieldValue));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression(fieldName + " = :val").withExpressionAttributeValues(expressionAttributeValues);
        return client.scan(scanRequest);
    }
}
