package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountBrokerEntity {

    private Integer id;

    private Integer accountId;

    private String symbol;

    private String name;

    private Double currentUnit;

    private AccountValueEntity value;

    @Override
    public String toString() {
        return "{\n" + "    \"accountId\": " + accountId + ",\n" + "    \"symbol\": \"" + symbol + "\",\n" + "    \"name\": \"" + name + "\"\n" + "}";
    }
}
