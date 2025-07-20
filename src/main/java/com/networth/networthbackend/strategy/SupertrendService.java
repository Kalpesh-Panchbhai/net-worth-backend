package com.networth.networthbackend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.service.RestAPIService;
import com.networth.networthbackend.service.StockValueChecker;
import lombok.SneakyThrows;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
    public void execute(Boolean fromAWS, Boolean sendAll) {
        List<String> symbols = readSymbols();
        List<SupertrendResponse> supertrendResponseList = new ArrayList<>();
        int totalRecords = symbols.size();
        for (int i = 1; i <= totalRecords; i++) {
            String symbol = symbols.get(i - 1);
            try {
                String jsonResponse = fetchHistoricalData(symbol);
                String symbolName = fetchStockName(jsonResponse);
                double currentMarketPrice = fetchStockCurrentMarketPrice(jsonResponse);
                BarSeries series = buildSeriesFromYahooResponse(symbol, jsonResponse);
                if (series.getBarCount() == 0) {
                    if (!fromAWS) {
                        SupertrendResponse supertrendResponse = new SupertrendResponse();
                        supertrendResponse.setSymbolName(symbolName);
                        supertrendResponse.setSymbol(symbol);
                        supertrendResponse.setHasError(true);
                        supertrendResponse.setErrorMessage("No data");
                        supertrendResponseList.add(supertrendResponse);
                    }
                    continue;
                }
                SuperTrendIndicator st = new SuperTrendIndicator(series, 10, 3.0);
                int endIndex = series.getEndIndex();
                if (endIndex < 1) {
                    if (!fromAWS) {
                        SupertrendResponse supertrendResponse = new SupertrendResponse();
                        supertrendResponse.setSymbolName(symbolName);
                        supertrendResponse.setSymbol(symbol);
                        supertrendResponse.setHasError(true);
                        supertrendResponse.setErrorMessage("Not enough data");
                        supertrendResponseList.add(supertrendResponse);
                    }
                    continue;
                }

                boolean lastUp = series.getBar(endIndex).getClosePrice().isGreaterThan(st.getValue(endIndex));
                boolean prevUp = series.getBar(endIndex - 1).getClosePrice().isGreaterThan(st.getValue(endIndex - 1));

                if (sendAll) {
                    SupertrendResponse supertrendResponse = new SupertrendResponse();
                    supertrendResponse.setSymbolName(symbolName);
                    supertrendResponse.setSymbol(symbol);
                    supertrendResponse.setSupertrendValue(st.getValue(endIndex).doubleValue());
                    supertrendResponse.setPotentialProfit((st.getValue(endIndex).doubleValue() / currentMarketPrice * 100) - 100);
                    supertrendResponse.setShouldBuy(!lastUp && prevUp);
                    supertrendResponseList.add(supertrendResponse);
                } else {
                    if (!lastUp && prevUp) {
                        SupertrendResponse supertrendResponse = new SupertrendResponse();
                        supertrendResponse.setSymbolName(symbolName);
                        supertrendResponse.setSymbol(symbol);
                        supertrendResponse.setSupertrendValue(st.getValue(endIndex).doubleValue());
                        supertrendResponse.setPotentialProfit((st.getValue(endIndex).doubleValue() / currentMarketPrice * 100) - 100);
                        supertrendResponse.setShouldBuy(true);
                        supertrendResponseList.add(supertrendResponse);
                    }
                }
                printProgressBar(i, totalRecords);
            } catch (Exception e) {
                System.err.println("Error processing " + symbol + " - " + e.getMessage());
            }
        }
        supertrendResponseList.sort(Comparator.comparingDouble(SupertrendResponse::getPotentialProfit).reversed());
        for (int i = 0; i < supertrendResponseList.size(); i++) {
            supertrendResponseList.get(i).setId(getNumberEmoji(i + 1));
        }
        if (!fromAWS) {
            printMessageToConsole(supertrendResponseList);
        } else {
            sendMessageToTelegram(supertrendResponseList);
        }
    }

    private void printMessageToConsole(List<SupertrendResponse> supertrendResponseList) {
        System.out.println();
        supertrendResponseList.forEach((msg) -> {
            if (msg.getHasError()) {
                String symbolResponse = String.format("%-20s%-100s%-20s%-20s", msg.getId(), msg.getSymbolName(), msg.getSymbol(), msg.getErrorMessage());
                System.out.println(symbolResponse);
            } else {
                String symbolResponse = String.format("%-20s%-100s%-20s%-20.2f%-6.2f%%            %s", msg.getId(), msg.getSymbolName(), msg.getSymbol(), msg.getSupertrendValue(), msg.getPotentialProfit(), msg.getShouldBuy());
                System.out.println(symbolResponse);
            }
        });
    }

    private void sendMessageToTelegram(List<SupertrendResponse> supertrendResponseList) {
        supertrendResponseList.forEach((msg) -> {
            if (!msg.getHasError()) {
                String symbolName = escapeMarkdownV2(msg.getSymbolName());
                String symbol = escapeMarkdownV2(msg.getSymbol());
                BigDecimal bd = new BigDecimal(msg.getSupertrendValue().toString());
                bd = bd.setScale(2, RoundingMode.CEILING);
                String value = escapeMarkdownV2(bd.toString());
                bd = new BigDecimal(msg.getPotentialProfit().toString());
                bd = bd.setScale(2, RoundingMode.CEILING);
                String potentialProfitPercentageString = escapeMarkdownV2(bd.toString());
                String symbolResponse = String.format("%s%n📌 *Name: %s*%n🔖 `Symbol: %s`%n💰 Price: ₹%s%n📊 Potential Profit: \\+%s %%", msg.getId(), symbolName, symbol, value, potentialProfitPercentageString);
                try {
                    restAPIService.executePostAPI(symbolResponse);
                } catch (Exception e) {
                    System.out.println("Error posting " + symbolResponse + " - " + e.getMessage());
                }
            }
        });
    }

    /**
     * Escapes special characters for Telegram MarkdownV2 format
     * Characters that need escaping: _ * [ ] ( ) ~ ` > # + - = | { } . !
     */
    private String escapeMarkdownV2(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("([_*\\[\\]()~`>#+=|{}.!-])", "\\\\$1");
    }

    private String getNumberEmoji(int number) {
        String[] digitEmojis = {"0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣"};

        StringBuilder emojiBuilder = new StringBuilder();
        String numStr = String.valueOf(number);

        for (char ch : numStr.toCharArray()) {
            int digit = ch - '0';
            if (digit >= 0 && digit <= 9) {
                emojiBuilder.append(digitEmojis[digit]);
            }
        }

        return emojiBuilder.toString();
    }

    private List<String> readSymbols() throws IOException {
        URL resource = getClass().getClassLoader().getResource("stock_tradingview.txt");
        Path path = Paths.get(Objects.requireNonNull(resource).getPath());
        if (!Files.exists(path)) {
            throw new IOException("stock_tradingview.txt not found at " + STOCK_FILE);
        }
        List<String> raw = Files.readAllLines(path);
        List<String> symbols = new ArrayList<>();
        for (String line : raw) {
            String s = line.trim();
            if (!s.isEmpty() && !s.startsWith("#")) {
                StockValueChecker.StockData stock = parseStockLine(line);
                if (stock != null) {
                    symbols.add(stock.symbol);
                }
            }
        }
        return symbols;
    }

    private StockValueChecker.StockData parseStockLine(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                System.err.println("Invalid line format (expected SYMBOL,PRICE,MATCHING): " + line);
                return null;
            }

            String symbol = parts[0].trim();
            double price = Double.parseDouble(parts[1].trim());
            String matching = parts[2].trim();

            return new StockValueChecker.StockData(symbol, price, matching);
        } catch (NumberFormatException e) {
            System.err.println("Invalid price format in line: " + line);
            return null;
        }
    }

    private String fetchHistoricalData(String symbol) throws IOException, URISyntaxException, InterruptedException {
        String url = YAHOO_CHART_URL + symbol + "?interval=1d&range=1y";
        return restAPIService.executeAPI(url);
    }

    private String fetchStockName(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode resultNode = root.path("chart").path("result");
        if (!resultNode.isArray() || resultNode.isEmpty()) {
            return "";
        }
        JsonNode first = resultNode.get(0);
        JsonNode quote = first.path("meta").path("longName");
        return quote.asText();
    }

    private Double fetchStockCurrentMarketPrice(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode resultNode = root.path("chart").path("result");
        if (!resultNode.isArray() || resultNode.isEmpty()) {
            return 0.0;
        }
        JsonNode first = resultNode.get(0);
        JsonNode quote = first.path("meta").path("regularMarketPrice");
        return quote.asDouble();
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
}
