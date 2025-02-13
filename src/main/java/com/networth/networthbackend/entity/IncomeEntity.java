package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
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
