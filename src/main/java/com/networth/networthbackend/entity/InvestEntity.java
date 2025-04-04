package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InvestEntity {

    private Integer id;

    private Integer accountId;

    private Integer accountBrokerId;

    private String date;

    private Double value;

    private String operation;

    @Override
    public String toString() {
        return "{\n" +
                "    \"accountId\": " + accountId + ",\n" +
                "    \"accountBrokerId\": " + accountBrokerId + ",\n" +
                "    \"date\":  \"" + date + "\",\n" +
                "    \"value\": " + value + ",\n" +
                "    \"operation\": \"" + operation + "\"\n" +
                "}";
    }
}
