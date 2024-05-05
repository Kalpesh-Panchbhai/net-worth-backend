package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncomeRest {

    private String id;

    private String userId;

    private Double amount;

    private Double taxPaid;

    private String creditedOn;

    private String currency;

    private String incomeType;

    private String incomeTag;
}
