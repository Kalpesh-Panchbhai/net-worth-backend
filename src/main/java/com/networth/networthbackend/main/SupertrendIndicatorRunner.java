package com.networth.networthbackend.main;

import com.networth.networthbackend.strategy.SupertrendService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

public class SupertrendIndicatorRunner {

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        System.out.println(LocalDateTime.now());
        SupertrendService supertrendService = new SupertrendService();
        if (Boolean.parseBoolean(args[0])) {
            supertrendService.executeHistorical();
        } else {
            supertrendService.execute(Boolean.valueOf(args[1]), Boolean.valueOf(args[2]));
        }
        System.out.println(LocalDateTime.now());
    }
}
