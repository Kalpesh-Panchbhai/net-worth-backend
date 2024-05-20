package com.networth.networthbackend.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBDocument
public class BalanceDetailRest {

    @DynamoDBAttribute(attributeName = "dateTime")
    private String dateTime;

    @DynamoDBAttribute(attributeName = "value")
    private Double value = 0.0;
}
