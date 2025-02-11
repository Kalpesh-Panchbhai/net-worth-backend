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
public class AccountTransactionEntity {

    private Integer id;

    private Integer accountId;

    private Integer accountBrokerId;

    private Date transactionDate;

    private Double value;
}
