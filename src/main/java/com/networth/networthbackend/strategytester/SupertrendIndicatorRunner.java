package com.networth.networthbackend.strategytester;

import com.networth.networthbackend.strategy.SupertrendService;

import java.time.LocalDateTime;

public class SupertrendIndicatorRunner {

    public static void main(String[] args) {
        System.out.println(LocalDateTime.now());
        new SupertrendService().execute(false, Boolean.valueOf(args[0]));
        System.out.println(LocalDateTime.now());
    }
}
