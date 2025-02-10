package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AccountEntity {

    private String id;

    private String userId;

    private String name;

    private String type;

    private String currency;

    private Boolean active;
}
