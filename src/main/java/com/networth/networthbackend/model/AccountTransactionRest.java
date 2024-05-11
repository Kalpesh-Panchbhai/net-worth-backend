package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountTransactionRest {

    private String accountId;

    private Double amount;

    private String operation;

    private Boolean isAccountTransaction;
}