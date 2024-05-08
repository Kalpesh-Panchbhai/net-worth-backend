package com.networth.networthbackend.model.yahooapimodel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartData {
    private LocalDate localDate;
    private Double value;
}
