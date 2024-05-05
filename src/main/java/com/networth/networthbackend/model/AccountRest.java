package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountRest {

    private String id;

    private String userId;

    private String accountName;

    private String accountType;

    private String loanType;

    private String currency;

    private Boolean active;

    private BalanceRest balance;
}
