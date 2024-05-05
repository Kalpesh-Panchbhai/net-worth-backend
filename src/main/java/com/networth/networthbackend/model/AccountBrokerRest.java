package com.networth.networthbackend.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountBrokerRest {

    private String id;

    private String accountId;

    private String symbol;

    private String name;

    private BalanceRest balance;
}
