package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class WatchEntity {

    private String id;

    private String userId;

    private String name;

    private List<String> accountList;

}
