package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class IncomeEntity {

    private Integer id;

    private String userId;

    private Double amount;

    private Double taxPaid;

    private Date creditedOn;

    private String currency;

    private String incomeType;

    private String incomeTag;
}
