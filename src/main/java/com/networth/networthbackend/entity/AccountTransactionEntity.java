package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountTransactionEntity {

    private Integer id;

    private Integer accountId;

    private Integer accountBrokerId;

    private String transactionDate;

    private Double value;

    private String operation;
}
