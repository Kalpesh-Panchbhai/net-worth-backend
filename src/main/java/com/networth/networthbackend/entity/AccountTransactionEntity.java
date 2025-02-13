package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AccountTransactionEntity {

    private Integer id;

    private Integer accountId;

    private Integer accountBrokerId;

    private String transactionDate;

    private Double value;
}
