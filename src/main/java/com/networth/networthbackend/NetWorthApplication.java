package com.networth.networthbackend;


import com.networth.networthbackend.service.AccountBalanceService;

import java.io.IOException;
import java.net.URISyntaxException;

public class NetWorthApplication {

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        AccountBalanceService accountBalanceService = new AccountBalanceService();
        accountBalanceService.generateChartData();
    }
}
