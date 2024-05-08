package com.networth.networthbackend.model.yahooapimodel;

import lombok.Data;

import java.util.List;

@Data
public class Indicators {
    private List<Quote> quote;
}
