package com.networth.networthbackend.main;

import com.networth.networthbackend.strategy.SupertrendService;

import java.time.LocalDateTime;

public class SupertrendIndicatorRunner {

    public static void main(String[] args) {
        System.out.println(LocalDateTime.now());
        new SupertrendService().execute(Boolean.valueOf(args[0]), Boolean.valueOf(args[1]));
        System.out.println(LocalDateTime.now());
    }
}
