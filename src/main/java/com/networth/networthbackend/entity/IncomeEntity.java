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

    private Integer id;

    private Integer userId;

    private Double amount;

    private Double taxPaid;

    private String creditedDate;

    private String currency;

    private Integer incomeType;

    private Integer incomeTag;
}
