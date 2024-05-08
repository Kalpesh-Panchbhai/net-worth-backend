package com.networth.networthbackend.model.yahooapimodel;

import lombok.Data;

import java.util.List;

@Data
public class Quote {
    private List<String> close;
}
