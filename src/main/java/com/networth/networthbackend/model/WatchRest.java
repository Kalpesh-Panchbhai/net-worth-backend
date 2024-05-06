package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WatchRest {

    private String id;

    private String userId;

    private String name;

    private List<String> accountList;
}
