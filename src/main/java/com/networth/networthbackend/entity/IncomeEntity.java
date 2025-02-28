package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IncomeEntity {

    private Integer id;

    private Integer userId;

    private Double amount;

    private Double taxPaid;

    private String creditedDate;

    private String currency;

    private IncomeTypeEntity incomeType;

    private IncomeTagEntity incomeTag;

    @Override
    public String toString() {
        return "{\n" +
                "    \"userId\": " + userId+ ",\n" +
                "    \"amount\": " + amount + ",\n" +
                "    \"taxPaid\": " + taxPaid+",\n" +
                "    \"creditedDate\": \"" + creditedDate + "\",\n" +
                "    \"currency\": \"" + currency + "\",\n" +
                "    \"incomeType\": {\n" +
                "        \"id\": " + incomeType.getId() +",\n" +
                "        \"userId\": 1,\n" +
                "        \"name\": \"G\",\n" +
                "        \"isDefault\": false\n" +
                "    },\n" +
                "    \"incomeTag\": {\n" +
                "        \"id\": " + incomeTag.getId()+ ",\n" +
                "        \"userId\": 1,\n" +
                "        \"name\": \"A\",\n" +
                "        \"isDefault\": false\n" +
                "    }\n" +
                "}";
    }
}
