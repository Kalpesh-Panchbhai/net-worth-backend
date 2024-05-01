package com.networth.networthbackend;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.entity.Employee;

public class LambdaScheduler implements RequestHandler<Void, Void> {

    @Override
    public Void handleRequest(Void unused, Context context) {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("dynamodb.ap-south-1.amazonaws.com", "ap-south-1")).withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("AKIAUF72G2OIKQGZV6P4", "Jqtdn3kfTDzWh3JiI2A5c0UJsWSEJKiZiy0phsPo"))).build();
        DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(client);
        Employee employee = new Employee();
        employee.setName("Kalpesh1111");
        employee.setEmail("Kalpesh1111@gmail.com");
        dynamoDBMapper.save(employee);
        return null;
    }
}
