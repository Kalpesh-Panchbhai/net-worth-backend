package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountTransactionRest {

    private String id;

    private String accountId;

    private String timestamp;

    private Double amount;

    private Boolean paid;

    private String operation;

}
