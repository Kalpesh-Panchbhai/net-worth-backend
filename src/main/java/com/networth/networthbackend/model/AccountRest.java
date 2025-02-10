package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountRest {

    private Integer id;

    private String userId;

    private String name;

    private String type;

    private String currency;

    private Boolean active;
}
