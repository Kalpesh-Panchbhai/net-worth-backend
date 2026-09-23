package com.stock.analyzer.backtest.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.analyzer.backtest.model.Bar;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches OHLCV data directly from Yahoo Finance v8 API (no cache).
 * Used by the live stock-signal scan for real-time signal generation.
 *
 * <p>API endpoint: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *
 * <p>Ported from the stock-analyzer engine to use the JDK {@link HttpClient} rather than OkHttp, so
 * folding the engine into this Lambda service adds no new runtime dependency (and no Kotlin stdlib
 * bloat in the uber-jar).
 */
public class YahooFinanceFetcher {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Shared across instances: an {@link HttpClient} owns a selector thread and a connection pool,
     * so one per caller would leak threads and skip connection reuse across the ~500 symbol fetches
     * a full scan issues.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    private final ZoneId zone;

    public YahooFinanceFetcher() {
        this(ZoneId.of("Asia/Kolkata"));
    }

    /** @param timezone IANA zone of the exchange (e.g. "America/New_York"). */
    public YahooFinanceFetcher(String timezone) {
        this(ZoneId.of(timezone));
    }

    public YahooFinanceFetcher(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * Fetch daily OHLCV bars from Yahoo Finance.
     *
     * @param symbol Ticker (e.g. "RELIANCE.NS")
     * @param from   Start date (inclusive)
     * @return List of Bar objects, empty on failure
     */
    public List<Bar> fetchDaily(String symbol, LocalDate from) {
        List<Bar> bars = new ArrayList<>();

        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = Instant.now().getEpochSecond();

        String url = BASE_URL + symbol
            + "?period1=" + period1
            + "&period2=" + period2
            + "&interval=1d"
            + "&events=history";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .GET()
            .build();

        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2 || response.body() == null) {
                System.err.println("  ⚠ Yahoo API error for " + symbol + ": HTTP " + response.statusCode());
                return bars;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode chart = root.path("chart").path("result");
            if (!chart.isArray() || chart.isEmpty()) return bars;

            JsonNode result = chart.get(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode indicators = result.path("indicators").path("quote").get(0);

            if (!timestamps.isArray() || timestamps.isEmpty() || indicators == null) return bars;

            JsonNode opens = indicators.path("open");
            JsonNode highs = indicators.path("high");
            JsonNode lows = indicators.path("low");
            JsonNode closes = indicators.path("close");
            JsonNode volumes = indicators.path("volume");

            // Use adjusted close if available (splits + dividends)
            JsonNode adjCloseNode = result.path("indicators").path("adjclose");
            JsonNode adjCloses = null;
            if (adjCloseNode.isArray() && !adjCloseNode.isEmpty()) {
                adjCloses = adjCloseNode.get(0).path("adjclose");
            }

            for (int i = 0; i < timestamps.size(); i++) {
                if (opens.get(i).isNull() || closes.get(i).isNull()) continue;

                long ts = timestamps.get(i).asLong();
                LocalDate date = Instant.ofEpochSecond(ts)
                    .atZone(zone)
                    .toLocalDate();

                double open = opens.get(i).asDouble();
                double high = highs.get(i).asDouble();
                double low = lows.get(i).asDouble();
                double close = closes.get(i).asDouble();
                double volume = volumes.get(i).isNull() ? 0 : volumes.get(i).asDouble();

                // Apply adjustment ratio from adjusted close
                if (adjCloses != null && !adjCloses.get(i).isNull()) {
                    double adjClose = adjCloses.get(i).asDouble();
                    if (close > 0) {
                        double ratio = adjClose / close;
                        open *= ratio;
                        high *= ratio;
                        low *= ratio;
                        close = adjClose;
                    }
                }

                if (open <= 0 || high <= 0 || low <= 0 || close <= 0) continue;
                // Clamp OHLC consistency
                high = Math.max(high, Math.max(open, close));
                low = Math.min(low, Math.min(open, close));

                bars.add(new Bar(date, open, high, low, close, volume));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("  ⚠ Yahoo fetch interrupted for " + symbol);
        } catch (Exception e) {
            System.err.println("  ⚠ Yahoo fetch failed for " + symbol + ": " + e.getMessage());
        }

        return bars;
    }
}
