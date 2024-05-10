package com.networth.networthbackend;


import com.networth.networthbackend.service.ChartDataGenerator;

import java.io.IOException;
import java.net.URISyntaxException;

public class NetWorthApplication {

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        ChartDataGenerator chartDataGenerator = new ChartDataGenerator();
        chartDataGenerator.generateChartData();
    }
}
