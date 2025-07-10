package com.networth.networthbackend.strategy;

import com.networth.networthbackend.service.RestAPIService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class to download the full list of NSE equities (~2000 symbols) and
 * write them (one per line) to {@code src/main/resources/stocks.txt} so that
 * SupertrendService can process them.
 * <p>
 * We leverage NSE's publicly available CSV – {@code EQUITY_L.csv}. This file contains
 * all active equities and is periodically updated by the exchange.
 */
public class StockListUpdater {

    private static final String NSE_CSV_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    private static final String STOCK_FILE = "src/main/resources/stocks.txt";

    private final RestAPIService restAPIService = new RestAPIService();

    @SneakyThrows
    public void update() {
        String csv = downloadCsv();
        List<String> symbols = parseSymbols(csv)
                .stream()
                .map(s -> s + ".NS")
                .collect(Collectors.toList());

        writeSymbols(symbols);
        System.out.println("Final Yahoo-tradable NSE symbols: " + symbols.size());
    }

    private String downloadCsv() throws IOException, URISyntaxException, InterruptedException {
        return restAPIService.executeAPI(NSE_CSV_URL);
    }

    private List<String> parseSymbols(String csv) {
        return csv.lines()
                .skip(1) // header line
                .map(line -> line.split(",", -1)[0]) // SYMBOL column
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private void writeSymbols(List<String> symbols) throws IOException {
        Path path = Paths.get(STOCK_FILE);
        Files.createDirectories(path.getParent());
        Files.write(path, symbols);
    }

    public static void main(String[] args) {
        new StockListUpdater().update();
    }
}
