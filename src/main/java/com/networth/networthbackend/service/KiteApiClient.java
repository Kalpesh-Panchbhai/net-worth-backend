package com.networth.networthbackend.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.MFHolding;
import com.zerodhatech.models.User;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class KiteApiClient {

    private final String apiKey;
    private final String apiSecret;
    private final KiteConnect kiteConnect;

    public KiteApiClient() {
        this.apiKey = System.getenv("KITE_API_KEY");
        this.apiSecret = System.getenv("KITE_API_SECRET");
        this.kiteConnect = (apiKey != null && !apiKey.isBlank()) ? new KiteConnect(apiKey) : null;
    }

    public boolean isAvailable() {
        return kiteConnect != null && apiSecret != null && !apiSecret.isBlank();
    }

    public String getLoginUrl() {
        return kiteConnect.getLoginURL();
    }

    /**
     * Exchange request token for access token using the official Kite SDK.
     * Wraps KiteException (which extends Throwable) into RuntimeException.
     */
    public String exchangeToken(String requestToken) throws KiteException, IOException {
        User user = kiteConnect.generateSession(requestToken, apiSecret);
        kiteConnect.setAccessToken(user.accessToken);
        log.info("Kite session generated for user: {}", user.userName);
        return user.accessToken;
    }

    public void setAccessToken(String accessToken) {
        kiteConnect.setAccessToken(accessToken);
    }

    public List<MFHolding> getMfHoldings(String accessToken) throws KiteException, IOException {
        kiteConnect.setAccessToken(accessToken);
        return kiteConnect.getMFHoldings();
    }
}
