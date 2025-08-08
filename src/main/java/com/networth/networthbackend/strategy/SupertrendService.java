package com.networth.networthbackend.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.service.RestAPIService;
import com.networth.networthbackend.service.StockValueChecker;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SupertrendService {
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestAPIService restAPIService = new RestAPIService();
    private final ObjectMapper mapper = new ObjectMapper();

    public static void printProgressBar(int current, int total) {
        int barLength = 100;
        int progress = (int) ((double) current / total * barLength);
        int percent = (int) ((double) current / total * 100);

        StringBuilder bar = new StringBuilder();
        bar.append("\r[");

        for (int i = 0; i < barLength; i++) {
            bar.append(i < progress ? "=" : " ");
        }

        bar.append("] ").append(percent).append("% (").append(current).append("/").append(total).append(")");

        System.out.print(bar);
    }

    @SneakyThrows
    public void execute(Boolean fromAWS, Boolean sendAll) {
        List<String> symbols = readSymbols("stocks_in.txt");
        List<SupertrendResponse> supertrendResponseList = new ArrayList<>();
        int totalRecords = symbols.size();
        for (int i = 1; i <= totalRecords; i++) {
            String symbol = symbols.get(i - 1);
            try {
                String jsonResponse = fetchHistoricalData(symbol, "1y");
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
                String tradingViewSymbol = msg.getSymbol().replace(".NS", "").split("-")[0];
                String tradingViewUrl = "https://www.tradingview.com/chart/?symbol=NSE:" + tradingViewSymbol;
                String symbolResponse = String.format("%s%n📌 *Name: %s*%n🔖 `Symbol: %s`%n💰 Price: ₹%s%n📊 Potential Profit: \\+%s %% %n📈 [Chart](%s)", msg.getId(), symbolName, symbol, value, potentialProfitPercentageString, tradingViewUrl);
                try {
                    restAPIService.executePostAPI(symbolResponse);
                } catch (Exception e) {
                    System.out.println("Error posting " + symbolResponse + " - " + e.getMessage());
                }
            }
        });
    }

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

    public List<String> readSymbols(String fileName) throws IOException {
        URL resource = getClass().getClassLoader().getResource(fileName);
        Path path = Paths.get(Objects.requireNonNull(resource).getPath());
        if (!Files.exists(path)) {
            throw new IOException(fileName + " not found at src/main/resources/");
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
            if (parts.length != 3 && parts.length != 1) {
                System.err.println("Invalid line format (expected SYMBOL,PRICE,MATCHING): " + line);
                return null;
            }

            if (parts.length == 1) {
                String symbol = parts[0].trim();
                return new StockValueChecker.StockData(symbol, 0.0, "");
            } else {
                String symbol = parts[0].trim();
                double price = Double.parseDouble(parts[1].trim());
                String matching = parts[2].trim();
                return new StockValueChecker.StockData(symbol, price, matching);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid price format in line: " + line);
            return null;
        }
    }

    private String fetchHistoricalData(String symbol, String year) throws IOException, URISyntaxException, InterruptedException {
        String url = YAHOO_CHART_URL + symbol + "?interval=1d&range=" + year;
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
        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        try {
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

            int len = timestamps.size();
            for (int i = 0; i < len; i++) {
                if (opens.get(i).isNull()) continue;
                ZonedDateTime time = Instant.ofEpochSecond(timestamps.get(i).asLong()).atZone(ZoneId.systemDefault());
                double open = opens.get(i).asDouble();
                double high = highs.get(i).asDouble();
                double low = lows.get(i).asDouble();
                double close = closes.get(i).asDouble();
                double volume = volumes.get(i).asDouble();
                series.addBar(new BaseBar(Duration.ofDays(1), time, open, high, low, close, volume));
            }
        } catch (Exception e) {
            System.out.println("Error building series for " + symbol + ": " + e.getMessage());
        }
        return series;
    }

    public SupertrendTradeStats simulateSupertrendTrades(String symbol) throws IOException, URISyntaxException, InterruptedException {
        String jsonResponse = fetchHistoricalData(symbol, "100y");
        BarSeries series = buildSeriesFromYahooResponse(symbol, jsonResponse);
        List<Trade> trades = new ArrayList<>();
        if (series.getBarCount() < 2) return new SupertrendTradeStats(trades, 0, 0, 0.0, 0.0, 0);
        SuperTrendIndicator st = new SuperTrendIndicator(series, 10, 3.0);
        List<Trade> openBuys = new ArrayList<>();
        int orderIdCounter = 1;
        for (int i = 1; i < series.getBarCount() - 1; i++) {
            boolean prevUp = series.getBar(i - 1).getClosePrice().isGreaterThan(st.getValue(i - 1));
            boolean currUp = series.getBar(i).getClosePrice().isGreaterThan(st.getValue(i));
            if (!currUp && prevUp) {
                int next = i + 1;
                Trade buyTrade = new Trade("BUY", series.getBar(next).getEndTime(), series.getBar(next).getOpenPrice().doubleValue(), orderIdCounter++);
                openBuys.add(buyTrade);
                trades.add(buyTrade);
            }
            List<Trade> sellsToAdd = new ArrayList<>();
            List<Trade> buysToRemove = new ArrayList<>();
            for (Trade buy : openBuys) {
                double buyPrice = buy.price;
                if (currUp && series.getBar(i).getClosePrice().doubleValue() > buyPrice) {
                    int next = i + 1;
                    Trade sellTrade = new Trade("SELL", series.getBar(next).getEndTime(), series.getBar(next).getOpenPrice().doubleValue(), buy.orderId);
                    sellsToAdd.add(sellTrade);
                    buysToRemove.add(buy);
                }
            }
            trades.addAll(sellsToAdd);
            openBuys.removeAll(buysToRemove);
        }
        int totalTradeDays = 0;
        int totalTrades = 0;
        double initialCapital = 1.0;
        double topupCapital = 0.0;
        Map<Integer, Trade> buyMap = new HashMap<>();
        Map<Integer, Double> buyCapital = new HashMap<>();
        for (Trade trade : trades) {
            if ("BUY".equals(trade.type)) {
                if (!buyMap.isEmpty()) {
                    buyCapital.put(trade.orderId, 1.0);
                } else {
                    buyCapital.put(trade.orderId, initialCapital);
                }
                buyMap.put(trade.orderId, trade);
            } else if ("SELL".equals(trade.type)) {
                Trade lastBuy = buyMap.remove(trade.orderId);
                double boughtCapital = buyCapital.remove(trade.orderId);
                if (lastBuy != null && boughtCapital != 0.0) {
                    if (boughtCapital == 1.0 && trade.orderId != 1) {
                        topupCapital = topupCapital + (boughtCapital * (trade.price / lastBuy.price) - 1.0);
                    } else {
                        initialCapital = boughtCapital * (trade.price / lastBuy.price);
                    }
                    int days = (int) Duration.between(lastBuy.date.toLocalDate().atStartOfDay(), trade.date.toLocalDate().atStartOfDay()).toDays();
                    if (days <= 0) days = 1;
                    totalTradeDays += days;
                    totalTrades++;
                }
            }
        }
        double totalProfit = (initialCapital - 1.0 + topupCapital);
        double avgTradeDays = totalTrades > 0 ? (double) totalTradeDays / totalTrades : 0.0;
        double avgPerDay = totalTradeDays == 0 ? 0.0 : (totalProfit / totalTradeDays * 100);
        return new SupertrendTradeStats(trades, totalTrades, totalTradeDays, avgTradeDays, avgPerDay, openBuys.size());
    }

    public void executeHistorical(String fileName) throws IOException, URISyntaxException, InterruptedException {
        List<String> symbols = readSymbols(fileName);
        double total = 0.0;
        int count = 0;
        int index = 0;
        List<StockDayResult> results = new ArrayList<>();
        System.out.printf("%-5s %-20s %20s %12s %12s %16s %16s\n", "#", "Symbol", "Per Day Profit (%)", "#Trades", "TradeDays", "AvgTradeDays", "PendingTrades");
        System.out.println("---------------------------------------------------------------------------------------------------------------");
        for (String symbol : symbols) {
            SupertrendTradeStats stats = simulateSupertrendTrades(symbol);
            System.out.printf("%-5d %-20s %20.4f %12d %12d %16.2f %16s\n", index + 1, symbol, stats.avgPerDay, stats.totalTrades, stats.totalTradeDays, stats.avgTradeDays, stats.pendingTrades);
            results.add(new StockDayResult(symbol, stats.avgPerDay, stats.totalTrades, stats.totalTradeDays, stats.avgTradeDays, stats.pendingTrades));
            if (stats.totalTrades > 0) {
                total += stats.avgPerDay;
                count++;
            }
            index++;
        }
        double avg = 0.0;
        if (count > 0) {
            avg = total / count;
        }

        results = results.stream().sorted((o1, o2) -> Double.compare(o2.perDay, o1.perDay)).toList();
        int rank = 1;
        int totalTradesAllStocks = 0;
        int totalTradeDaysAllStocks = 0;
        int totalPendingTradesAllStocks = 0;
        for (StockDayResult r : results) {
            totalTradesAllStocks += r.totalTrades;
            totalTradeDaysAllStocks += r.tradeDays;
            totalPendingTradesAllStocks += r.pendingTrades;
        }
        double combinedAvgTradeDays = totalTradesAllStocks > 0 ? (double) totalTradeDaysAllStocks / totalTradesAllStocks : 0.0;
        String csvFileName = "supertrend_" + fileName.split("\\.")[0] + "_per_day_results_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")) + ".csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFileName))) {
            writer.println("Rank,Symbol,PerDayProfit,TotalTrades,TradeDays,AvgTradeDays,PendingTrades");
            writer.printf("#,%s,%.4f,%s,%s,%.2f,%s\n", "ALL STOCKS", avg, totalTradesAllStocks, totalTradeDaysAllStocks, combinedAvgTradeDays, totalPendingTradesAllStocks);
            for (StockDayResult r : results) {
                writer.printf("%d,%s,%.4f,%d,%d,%.2f,%d\n", rank++, r.symbol, r.perDay, r.totalTrades, r.tradeDays, r.avgTradeDays, r.pendingTrades);
            }

            System.out.println("\nCSV export complete: " + csvFileName);
        } catch (IOException e) {
            System.err.println("Error writing CSV file: " + e.getMessage());
        }
    }

    @Getter
    @Setter
    public static class Trade {
        public final String type;
        public final ZonedDateTime date;
        public final double price;
        public final int orderId;

        public Trade(String type, ZonedDateTime date, double price, int orderId) {
            this.type = type;
            this.date = date;
            this.price = price;
            this.orderId = orderId;
        }

        @Override
        public String toString() {
            return type + ", orderId=" + orderId + ", date=" + date.toLocalDate() + ", price=" + price;
        }
    }

    public record SupertrendTradeStats(List<Trade> trades, int totalTrades, int totalTradeDays, double avgTradeDays,
                                       double avgPerDay, int pendingTrades) {
    }

    public static class StockDayResult {
        String symbol;
        double perDay;
        int totalTrades;
        int tradeDays;
        double avgTradeDays;
        int pendingTrades;

        StockDayResult(String symbol, double perDay, int totalTrades, int tradeDays, double avgTradeDays, int pendingTrades) {
            this.symbol = symbol;
            this.perDay = perDay;
            this.totalTrades = totalTrades;
            this.tradeDays = tradeDays;
            this.avgTradeDays = avgTradeDays;
            this.pendingTrades = pendingTrades;
        }
    }

}
