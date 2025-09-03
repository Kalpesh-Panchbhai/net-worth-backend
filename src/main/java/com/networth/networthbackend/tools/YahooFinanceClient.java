package com.networth.networthbackend.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal Yahoo Finance chart API client.
 * Uses endpoint: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 * with params period1, period2 (unix seconds), interval=1d.
 */
public class YahooFinanceClient {

    private static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final int EXTRA_PAST_DAYS = 14; // fetch a cushion to cover holidays/weekends before start

    private final HttpClient http;
    private final ObjectMapper mapper;

    public YahooFinanceClient() {
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    private static String encodeSymbol(String symbol) {
        // Minimal encoding for symbols like ^GSPC etc.
        return symbol.replace("^", "%5E").replace("/", "%2F");
    }

    /**
     * Fetches daily close (prefers adjclose when available) for trading days within [start, end].
     * Also returns a calendar-day map with forward-filled values for missing calendar days.
     */
    public Map<LocalDate, BigDecimal> getDailyCloseCalendarFilled(String symbol, LocalDate start, LocalDate end) throws IOException, InterruptedException {
        if (end.isBefore(start)) return Map.of();

        LocalDate fetchStart = start.minusDays(EXTRA_PAST_DAYS);
        long period1 = fetchStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        // Yahoo chart API expects period2 as end in seconds; add one day to be inclusive
        long period2 = end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        String url = BASE + encodeSymbol(symbol) + "?interval=1d&period1=" + period1 + "&period2=" + period2;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "MyYahooClient/1.0 Net-Worth API").header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Yahoo API status=" + resp.statusCode() + " body=" + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) return Map.of();
        JsonNode r0 = result.get(0);

        JsonNode ts = r0.path("timestamp");
        JsonNode indicators = r0.path("indicators");
        JsonNode quote0 = indicators.path("quote").isArray() && indicators.path("quote").size() > 0 ? indicators.path("quote").get(0) : null;
        JsonNode closeArr = quote0 != null ? quote0.path("close") : null;
        JsonNode adjCloseArr = indicators.path("adjclose").isArray() && indicators.path("adjclose").size() > 0 ? indicators.path("adjclose").get(0).path("adjclose") : null;

        // Prefer adjclose, else close
        JsonNode prices = (adjCloseArr != null && adjCloseArr.isArray() && adjCloseArr.size() == ts.size()) ? adjCloseArr : closeArr;
        if (ts == null || !ts.isArray() || prices == null || !prices.isArray()) return Map.of();

        TreeMap<LocalDate, BigDecimal> trading = new TreeMap<>();
        for (int i = 0; i < ts.size(); i++) {
            JsonNode t = ts.get(i);
            JsonNode p = prices.get(i);
            if (t == null || t.isNull() || p == null || p.isNull()) continue;
            long epochSec = t.asLong();
            LocalDate d = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal val = new BigDecimal(p.asText());
            trading.put(d, val);
        }

        // Forward-fill to calendar days
        Map<LocalDate, BigDecimal> calendar = new HashMap<>();
        BigDecimal last = trading.floorEntry(start) != null ? trading.floorEntry(start).getValue() : null;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (trading.containsKey(d)) {
                last = trading.get(d);
            }
            if (last != null) {
                calendar.put(d, last);
            }
            d = d.plusDays(1);
        }
        return calendar;
    }

    /**
     * Same as above but also returns the currency reported by Yahoo for the symbol.
     */
    public PriceSeries getDailyCloseWithCurrencyCalendarFilled(String symbol, LocalDate start, LocalDate end) throws IOException, InterruptedException {
        if (end.isBefore(start)) return new PriceSeries(Map.of(), null);

        LocalDate fetchStart = start.minusDays(EXTRA_PAST_DAYS);
        long period1 = fetchStart.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long period2 = end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        String url = BASE + encodeSymbol(symbol) + "?interval=1d&period1=" + period1 + "&period2=" + period2;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "MyYahooClient/1.0 Net-Worth API").header("Accept", "application/json").GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Yahoo API status=" + resp.statusCode() + " body=" + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) return new PriceSeries(Map.of(), null);
        JsonNode r0 = result.get(0);

        String currency = null;
        JsonNode meta = r0.path("meta");
        if (meta != null && !meta.isMissingNode()) {
            JsonNode c = meta.path("currency");
            if (c != null && !c.isMissingNode() && !c.isNull()) {
                currency = c.asText();
            }
        }

        JsonNode ts = r0.path("timestamp");
        JsonNode indicators = r0.path("indicators");
        JsonNode quote0 = indicators.path("quote").isArray() && indicators.path("quote").size() > 0 ? indicators.path("quote").get(0) : null;
        JsonNode closeArr = quote0 != null ? quote0.path("close") : null;
        JsonNode adjCloseArr = indicators.path("adjclose").isArray() && indicators.path("adjclose").size() > 0 ? indicators.path("adjclose").get(0).path("adjclose") : null;

        JsonNode prices = (adjCloseArr != null && adjCloseArr.isArray() && adjCloseArr.size() == ts.size()) ? adjCloseArr : closeArr;
        if (ts == null || !ts.isArray() || prices == null || !prices.isArray())
            return new PriceSeries(Map.of(), currency);

        TreeMap<LocalDate, BigDecimal> trading = new TreeMap<>();
        for (int i = 0; i < ts.size(); i++) {
            JsonNode t = ts.get(i);
            JsonNode p = prices.get(i);
            if (t == null || t.isNull() || p == null || p.isNull()) continue;
            long epochSec = t.asLong();
            LocalDate d = Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal val = new BigDecimal(p.asText());
            trading.put(d, val);
        }

        Map<LocalDate, BigDecimal> calendar = new HashMap<>();
        BigDecimal last = trading.floorEntry(start) != null ? trading.floorEntry(start).getValue() : null;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (trading.containsKey(d)) {
                last = trading.get(d);
            }
            if (last != null) {
                calendar.put(d, last);
            }
            d = d.plusDays(1);
        }
        return new PriceSeries(calendar, currency);
    }

    /**
     * Fetch FX pair daily close for converting from->to. For example, from=USD, to=INR => pair USDINR=X.
     * Returns forward-filled calendar-day rates in the same range.
     */
    public Map<LocalDate, BigDecimal> getFxCalendarFilled(String fromCcy, String toCcy, LocalDate start, LocalDate end) throws IOException, InterruptedException {
        if (fromCcy == null || toCcy == null || fromCcy.equalsIgnoreCase(toCcy)) {
            // 1:1 mapping
            Map<LocalDate, BigDecimal> ones = new HashMap<>();
            LocalDate d = start;
            while (!d.isAfter(end)) {
                ones.put(d, BigDecimal.ONE);
                d = d.plusDays(1);
            }
            return ones;
        }
        String pair = (fromCcy + toCcy + "=X").toUpperCase();
        return getDailyCloseCalendarFilled(pair, start, end);
    }

    /**
     * Wrapper with prices and the currency reported by Yahoo for the symbol.
     */
    public record PriceSeries(Map<LocalDate, BigDecimal> prices, String currency) {
    }
}
