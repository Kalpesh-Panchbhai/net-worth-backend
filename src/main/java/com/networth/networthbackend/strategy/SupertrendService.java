package com.networth.networthbackend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.service.RestAPIService;
import lombok.SneakyThrows;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class which reads symbols from {@code src/main/resources/stocks.txt},
 * fetches historical OHLCV data through the Yahoo Finance REST API using the existing
 * {@link RestAPIService}, constructs a {@link BarSeries} for each symbol and finally
 * calculates the Supertrend indicator (ATR period 10, multiplier 3) with ta4j.
 */
public class SupertrendService {

    private static final String STOCK_FILE = "src/main/resources/stocks.txt";
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestAPIService restAPIService = new RestAPIService();
    private final ObjectMapper mapper = new ObjectMapper();

    public static void printProgressBar(int current, int total) {
        int barLength = 100;
        int progress = (int) ((double) current / total * barLength);
        int percent = (int) ((double) current / total * 100);

        StringBuilder bar = new StringBuilder();
        bar.append("\r[");  // Carriage return to overwrite line

        for (int i = 0; i < barLength; i++) {
            bar.append(i < progress ? "=" : " ");
        }

        bar.append("] ").append(percent).append("% (").append(current).append("/").append(total).append(")");

        System.out.print(bar);
    }

    /**
     * Executes the complete E2E flow – read symbols, fetch data and calculate supertrend. The
     * resulting latest supertrend value (boolean trend + numeric line) is printed on console.
     */
    @SneakyThrows
    public void execute() {
        List<String> symbols = readSymbols();
        List<String> symbolResponseList = new ArrayList<>();
        int totalRecords = symbols.size();
        for (int i = 1; i <= totalRecords; i++) {
            String symbol = symbols.get(i - 1);
            try {
                String jsonResponse = fetchHistoricalData(symbol);
                BarSeries series = buildSeriesFromYahooResponse(symbol, jsonResponse);
                if (series.getBarCount() == 0) {
                    String symbolResponse = String.format("%-20s | No data", symbol);
                    symbolResponseList.add(symbolResponse);
                    continue;
                }
                SuperTrendIndicator st = new SuperTrendIndicator(series, 10, 3.0);
                int endIndex = series.getEndIndex();
                if (endIndex < 1) {
//                    String symbolResponse = String.format("%-20s | Not enough data", symbol);
//                    symbolResponseList.add(symbolResponse);
                    continue;
                }

                boolean lastUp = series.getBar(endIndex).getClosePrice().isGreaterThan(st.getValue(endIndex));
                boolean prevUp = series.getBar(endIndex - 1).getClosePrice().isGreaterThan(st.getValue(endIndex - 1));

                String verdict = (!lastUp && prevUp) ? "Valid" : "Invalid";

                if (!lastUp && prevUp) {
                    String symbolResponse = String.format("%-20s | ST: %-20.2f | PrevUp: %-5s, LastUp: %-5s -> %-10s", symbol, st.getValue(endIndex).doubleValue(), prevUp, lastUp, verdict);
                    symbolResponseList.add(symbolResponse);
                }
                printProgressBar(i, totalRecords);
            } catch (Exception e) {
                System.err.println("Error processing " + symbol + " - " + e.getMessage());
            }
        }
        System.out.println("\nAll records processed.");
        symbolResponseList.forEach(System.out::println);
    }

    private List<String> readSymbols() throws IOException {
        Path path = Paths.get(STOCK_FILE);
        if (!Files.exists(path)) {
            throw new IOException("stocks.txt not found at " + STOCK_FILE);
        }
        List<String> raw = Files.readAllLines(path);
        List<String> symbols = new ArrayList<>();
        for (String line : raw) {
            String s = line.trim();
            if (!s.isEmpty()) symbols.add(s);
        }
        return symbols;
    }

    private String fetchHistoricalData(String symbol) throws IOException, URISyntaxException, InterruptedException {
        String url = YAHOO_CHART_URL + symbol + "?interval=1d&range=1y";
        return restAPIService.executeAPI(url);
    }

    private BarSeries buildSeriesFromYahooResponse(String symbol, String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode resultNode = root.path("chart").path("result");
        if (!resultNode.isArray() || resultNode.isEmpty()) {
            return new BaseBarSeriesBuilder().withName(symbol).build();
        }
        JsonNode first = resultNode.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote").get(0);
        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");

        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        int len = timestamps.size();
        for (int i = 0; i < len; i++) {
            if (opens.get(i).isNull()) continue; // skip bad rows
            ZonedDateTime time = Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneId.systemDefault());
            double open = opens.get(i).asDouble();
            double high = highs.get(i).asDouble();
            double low = lows.get(i).asDouble();
            double close = closes.get(i).asDouble();
            double volume = volumes.get(i).asDouble();
            series.addBar(new BaseBar(Duration.ofDays(1), time, open, high, low, close, volume));
        }
        return series;
    }

    private void writeSymbols(List<String> symbols) throws IOException {
        Path path = Paths.get(STOCK_FILE);
        Files.createDirectories(path.getParent());
        Files.write(path, symbols);
    }
}
