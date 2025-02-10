package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class IncomeEntity {

    private String id;

    private String userId;

    private Double amount;

    private Double taxPaid;

    private String creditedOn;

    private String currency;

    private String incomeType;

    private String incomeTag;
}
