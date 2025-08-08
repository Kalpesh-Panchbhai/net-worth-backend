package com.networth.networthbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class that reads stock symbols and expected prices from stocks_in.txt file
 * and compares them with Yahoo Finance API data using the regularMarketPrice field.
 * After the first run, updates the file with .NS suffix and YES/NO match results.
 */
public class StockValueChecker {

    private static final String STOCK_FILE = "stocks_in.txt";
    private static final String YAHOO_FINANCE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestAPIService restAPIService;
    private final ObjectMapper mapper;
    private final List<StockResult> results;

    public StockValueChecker() {
        this.restAPIService = new RestAPIService();
        this.mapper = new ObjectMapper();
        this.results = new ArrayList<>();
    }

    /**
     * Main method to execute the stock value checker
     */
    public static void main(String[] args) {
        StockValueChecker checker = new StockValueChecker();
        try {
            checker.checkAllStocks();
            checker.updateStockFile();
        } catch (Exception e) {
            System.err.println("Error running stock value checker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reads all stocks from file and checks their values against Yahoo Finance API
     */
    public void checkAllStocks() throws IOException, URISyntaxException, InterruptedException {
        List<StockData> stocks = readStocksFromFile();
        System.out.println("Checking " + stocks.size() + " stocks from " + STOCK_FILE);
        System.out.println("=".repeat(60));

        for (StockData stock : stocks) {
            if (stock.matching.equals("YES")) {
                results.add(new StockResult(stock.symbol, stock.expectedPrice, stock.expectedPrice, true));
                continue;
            }
            checkSingleStock(stock);
        }
    }

    /**
     * Checks a single stock's value against Yahoo Finance API
     */
    private void checkSingleStock(StockData stock) {
        try {
            String suffix = ".NS";
            String url = YAHOO_FINANCE_URL + stock.symbol + suffix;
            String response = restAPIService.executeAPI(url);

            double apiPrice = parseRegularMarketPrice(response);
            boolean isEqual = Math.abs(stock.expectedPrice - apiPrice) == 0.00;

            System.out.printf("%-15s | Expected: %8.2f | API: %8.2f | Match: %s%n", stock.symbol, stock.expectedPrice, apiPrice, isEqual ? "YES" : "NO");

            if (isEqual) {
                results.add(new StockResult(stock.symbol + suffix, stock.expectedPrice, apiPrice, true));
            } else {
                results.add(new StockResult(stock.symbol, stock.expectedPrice, apiPrice, false));
            }
        } catch (Exception e) {
            System.out.printf("%-15s | Error: %s%n", stock.symbol, e.getMessage());
            results.add(new StockResult(stock.symbol, stock.expectedPrice, e.getMessage()));
        }
    }

    /**
     * Parses the Yahoo Finance API response to extract regularMarketPrice
     */
    private double parseRegularMarketPrice(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode resultNode = root.path("chart").path("result");

        if (!resultNode.isArray() || resultNode.isEmpty()) {
            throw new IOException("Invalid API response: no result data found");
        }

        JsonNode first = resultNode.get(0);
        JsonNode priceNode = first.path("meta").path("regularMarketPrice");

        if (priceNode.isMissingNode()) {
            throw new IOException("regularMarketPrice not found in API response");
        }

        return priceNode.asDouble();
    }

    /**
     * Reads stock symbols and expected prices from the stocks_in.txt file
     */
    private List<StockData> readStocksFromFile() throws IOException {
        List<StockData> stocks = new ArrayList<>();

        URL resource = getClass().getClassLoader().getResource(STOCK_FILE);
        if (resource == null) {
            throw new IOException(STOCK_FILE + " not found in resources folder");
        }

        try (InputStream inputStream = resource.openStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    StockData stock = parseStockLine(line);
                    if (stock != null) {
                        stocks.add(stock);
                    }
                }
            }
        }

        return stocks;
    }

    /**
     * Parses a single line from the stock file (format: SYMBOL,PRICE)
     */
    private StockData parseStockLine(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                System.err.println("Invalid line format (expected SYMBOL,PRICE,MATCHING): " + line);
                return null;
            }

            String symbol = parts[0].trim();
            double price = Double.parseDouble(parts[1].trim());
            String matching = parts[2].trim();

            return new StockData(symbol, price, matching);
        } catch (NumberFormatException e) {
            System.err.println("Invalid price format in line: " + line);
            return null;
        }
    }

    /**
     * Updates the stock file with .NS suffix and YES/NO match results
     */
    private void updateStockFile() throws IOException {
        System.out.println("\nUpdating " + STOCK_FILE + " with .NS suffix and YES/NO results...");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/main/resources/" + STOCK_FILE))) {
            for (StockResult result : results) {
                String matchResult = result.isMatch ? "YES" : "NO";
                if (result.hasError) {
                    writer.write(result.symbol + "," + result.expectedPrice + ",ERROR");
                } else {
                    writer.write(result.symbol + "," + result.expectedPrice + "," + matchResult);
                }
                writer.newLine();
            }
        }

        System.out.println("File updated successfully with " + results.size() + " stocks.");
    }

    /**
     * Data class to store stock processing results
     */
    private static class StockResult {
        String symbol;
        double expectedPrice;
        double apiPrice;
        boolean isMatch;
        boolean hasError;
        String errorMessage;

        StockResult(String symbol, double expectedPrice, double apiPrice, boolean isMatch) {
            this.symbol = symbol;
            this.expectedPrice = expectedPrice;
            this.apiPrice = apiPrice;
            this.isMatch = isMatch;
            this.hasError = false;
        }

        StockResult(String symbol, double expectedPrice, String errorMessage) {
            this.symbol = symbol;
            this.expectedPrice = expectedPrice;
            this.errorMessage = errorMessage;
            this.hasError = true;
            this.isMatch = false;
            this.apiPrice = 0.0;
        }
    }

    /**
     * Data class to hold stock symbol and expected price
     */
    public static class StockData {
        public final String symbol;
        final double expectedPrice;
        final String matching;

        public StockData(String symbol, double expectedPrice, String matching) {
            this.symbol = symbol;
            this.expectedPrice = expectedPrice;
            this.matching = matching;
        }
    }
}
