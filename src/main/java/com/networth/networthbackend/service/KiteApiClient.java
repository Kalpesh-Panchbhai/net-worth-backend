package com.networth.networthbackend.service;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.MFHolding;
import com.zerodhatech.models.Position;
import com.zerodhatech.models.User;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Kite Connect access.
 * <p>
 * {@link KiteConnect} carries the access token as mutable state, so a single shared instance
 * cannot serve concurrent callers: one request's {@code setAccessToken} would apply to another's
 * in-flight call. Each call therefore gets its own short-lived {@code KiteConnect}, which makes
 * this class safe to hold on a shared handler.
 */
@Slf4j
public class KiteApiClient {

    private final String apiKey;
    private final String apiSecret;

    public KiteApiClient() {
        this.apiKey = System.getenv("KITE_API_KEY");
        this.apiSecret = System.getenv("KITE_API_SECRET");
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    public String getLoginUrl() {
        return session(null).getLoginURL();
    }

    /** Exchanges a request token for an access token. */
    public String exchangeToken(String requestToken) throws KiteException, IOException {
        User user = session(null).generateSession(requestToken, apiSecret);
        log.info("Kite session generated for user: {}", user.userName);
        return user.accessToken;
    }

    public List<MFHolding> getMfHoldings(String accessToken) throws KiteException, IOException {
        return session(accessToken).getMFHoldings();
    }

    public List<Holding> getStockHoldings(String accessToken) throws KiteException, IOException {
        return session(accessToken).getHoldings();
    }

    /**
     * Net open positions for the day (intraday, F&amp;O and same-day delivery buys that have not
     * yet settled into holdings).
     */
    public List<Position> getStockPositions(String accessToken) throws KiteException, IOException {
        Map<String, List<Position>> positions = session(accessToken).getPositions();
        List<Position> net = positions.get("net");
        return net != null ? net : List.of();
    }

    private KiteConnect session(String accessToken) {
        KiteConnect kite = new KiteConnect(apiKey);
        if (accessToken != null) kite.setAccessToken(accessToken);
        return kite;
    }
}
