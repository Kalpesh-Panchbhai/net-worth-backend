package com.networth.networthbackend.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DynamoDBDocument
public class BalanceRest {

    @DynamoDBAttribute(attributeName = "currentDayValue")
    private BalanceDetailRest currentDayValue;

    @DynamoDBAttribute(attributeName = "previousDayValue")
    private BalanceDetailRest previousDayValue;
}
