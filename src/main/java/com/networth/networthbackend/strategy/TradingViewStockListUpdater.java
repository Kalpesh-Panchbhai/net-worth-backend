package com.networth.networthbackend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Downloads the complete list of NSE stocks available on TradingView using
 * TradingView's public scanner endpoint and stores the symbols in
 * {@code src/main/resources/stocks.txt} (one ticker per line, Yahoo suffix .NS).
 * <p>
 * Endpoint: https://scanner.tradingview.com/india/scan (POST)
 */
public class TradingViewStockListUpdater {

    private static final String TV_SCAN_URL = "https://scanner.tradingview.com/india/scan";
    private static final String STOCK_FILE = "src/main/resources/stocks.txt";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @SneakyThrows
    public void update() {
        String json = fetchTradingViewSymbols();
        List<String> symbols = parseSymbols(json);
        writeSymbols(symbols);
        System.out.println("Fetched " + symbols.size() + " NSE tickers from TradingView -> " + STOCK_FILE);
    }

    private String fetchTradingViewSymbols() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TV_SCAN_URL))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("TradingView scanner returned status " + resp.statusCode());
        }
        return resp.body();
    }

    private List<String> parseSymbols(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.path("data");
        List<String> symbols = new ArrayList<>();
        for (JsonNode node : data) {
            String full = node.path("s").asText(); // format: NSE:ACC
            if (full.startsWith("NSE:")) {
                String sym = full.substring(4); // ACC
                symbols.add(sym + ".NS");
            }
        }
        return symbols.stream().distinct().sorted().collect(Collectors.toList());
    }

    private void writeSymbols(List<String> symbols) throws IOException {
        Path p = Paths.get(STOCK_FILE);
        Files.createDirectories(p.getParent());
        Files.write(p, symbols);
    }

    public static void main(String[] args) {
        new TradingViewStockListUpdater().update();
    }
}
