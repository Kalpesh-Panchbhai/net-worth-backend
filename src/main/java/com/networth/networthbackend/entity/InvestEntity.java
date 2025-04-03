package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InvestEntity {

    private Integer id;

    private Integer accountBrokerId;

    private String date;

    private Double value;

    private String operation;

    @Override
    public String toString() {
        return "{\n" +
                "    \"accountBrokerId\": " + accountBrokerId + ",\n" +
                "    \"date\":  \"" + date + "\",\n" +
                "    \"value\": " + value + ",\n" +
                "    \"operation\": \"" + operation + "\"\n" +
                "}";
    }
}
