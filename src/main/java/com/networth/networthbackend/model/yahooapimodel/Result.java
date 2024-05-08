package com.networth.networthbackend.model.yahooapimodel;

import lombok.Data;

import java.util.List;

@Data
public class Result {
    private Meta meta;
    private List<String> timestamp;
    private Indicators indicators;
}
