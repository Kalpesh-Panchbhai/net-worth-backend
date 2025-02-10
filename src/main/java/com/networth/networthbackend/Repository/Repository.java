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
import com.networth.networthbackend.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Repository<T> implements RepositoryImpl<T> {

    public final DatabaseService databaseService;
    private final AmazonDynamoDB client;
    private final DynamoDBMapper dynamoDBMapper;
    private final Connection connection;
    public String tableName;

    public Repository() {
        client = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("dynamodb.ap-south-1.amazonaws.com", "ap-south-1")).withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("AKIAUF72G2OIBNECZK4F", "EXCUuNK4xD14hqxkrpB04l7XI9TR+JvuthBv9+tZ"))).build();
        dynamoDBMapper = new DynamoDBMapper(client);

        databaseService = new DatabaseService();
        connection = databaseService.getConnection();
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
    public void delete(String id) {
        try {
            String query = "delete from " + tableName + " where id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, id);
            pstmt.execute();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            databaseService.disconnect();
        }
    }

    @Override
    public T getByPrimaryField(String id, Class<T> type) {
        return dynamoDBMapper.load(type, id);
    }

    @Override
    public Optional<T> getByPrimaryField(String id) {
        try {
            String query = "select * from " + tableName + " where id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            List<T> list = generateEntityData(rs);
            return !list.isEmpty() ? Optional.of(list.get(0)) : Optional.empty();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            databaseService.disconnect();
        }
        return null;
    }

    @Override
    public List<T> generateEntityData(ResultSet rs) throws SQLException {
        return null;
    }

    @Override
    public ScanResult getBySecondaryField(String fieldName, String fieldValue) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(fieldValue));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression(fieldName + " = :val").withExpressionAttributeValues(expressionAttributeValues);
        return client.scan(scanRequest);
    }
}
