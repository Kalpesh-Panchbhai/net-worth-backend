package com.networth.networthbackend.main;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetWorthApplication {

    public static void main(String[] args) {
        // Run an embedded HTTP server exposing API endpoints locally
        try {
            int port = 8080;
            new HttpServerRunner().start(port);
        } catch (Exception e) {
            log.error("Failed to start HTTP server", e);
        }
    }
}
