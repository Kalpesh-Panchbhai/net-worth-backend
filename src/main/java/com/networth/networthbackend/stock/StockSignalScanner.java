package com.networth.networthbackend.stock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.Market;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.YahooFinanceFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.ClosedTrade;
import com.stock.analyzer.backtest.model.Position;
import com.stock.analyzer.backtest.strategy.Strategy;
import com.stock.analyzer.backtest.strategy.StrategyData;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Runs each sector's winning strategy over live Yahoo Finance daily bars and reports the current
 * BUY / SELL / HOLD / FLAT signal per stock, for one market. This is the request-shaped
 * counterpart to the stock-analyzer's offline {@code SectorAllPhasesOptimizer}: the optimizer picks
 * the winners (shipped as the classpath {@code stock/sector_winners[_us].csv}), and this class
 * replays them.
 *
 * <p>Ported from the stock-analyzer {@code LiveSignalScanner}. The signal state machine in
 * {@link #scanOneStock} is preserved verbatim. The differences that make it Lambda-safe: winners,
 * sector universes and the US "refined" variants are read from the <b>classpath</b> (no filesystem),
 * and the per-stock fetch+scan tasks run on a small thread pool so a full scan finishes in seconds.
 */
@Slf4j
public class StockSignalScanner {

    /** Live mode only needs ~1 year warm-up before trade start (EMA 200 ≈ 250 bars). */
    private static final LocalDate LIVE_WARMUP_START = LocalDate.parse("2017-01-01");
    private static final LocalDate TRADE_FROM = LocalDate.parse(BacktestConfig.OOS_TRADE_FROM);

    /** Minimum bars a symbol needs before we trust an indicator-driven signal. */
    private static final int MIN_BARS = 100;

    private static final int DEFAULT_THREADS = 12;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Market market;
    private final int threads;
    /** Registry variants (thousands) merged with any market-specific refined variants. */
    private final Map<String, Supplier<Strategy>> variants;

    public StockSignalScanner() {
        this(Market.of("in"), DEFAULT_THREADS);
    }

    public StockSignalScanner(Market market, int threads) {
        this.market = market;
        this.threads = Math.max(1, threads);
        LinkedHashMap<String, Supplier<Strategy>> all = VariantRegistry.buildAll();
        all.putAll(loadRefinedVariants(market));
        this.variants = all;
        log.info("StockSignalScanner ready: {} strategy variants, market={}", all.size(), market.code());
    }

    /**
     * Scans every stock in every sector that has a resolvable winning strategy and returns the
     * current signal for each. Never throws for a single stock: a fetch failure or a missing
     * strategy is logged and skipped so one bad symbol can't sink the whole scan.
     */
    public List<StockSignal> scan() {
        List<String[]> winners = loadWinners(market);
        if (winners.isEmpty()) {
            log.warn("No sector winners on classpath for market {}; nothing to scan", market.code());
            return List.of();
        }

        YahooFinanceFetcher fetcher = new YahooFinanceFetcher(market.timezone());
        List<Callable<StockSignal>> tasks = new ArrayList<>();

        for (String[] w : winners) {
            String sector = w[0];
            String stratName = w[1];
            Supplier<Strategy> factory = variants.get(stratName);
            if (factory == null) {
                log.warn("[{}] strategy not found for sector {}: {}", market.code(), sector, stratName);
                continue;
            }
            for (String stock : market.loadSectorStocks(sector)) {
                tasks.add(() -> {
                    List<Bar> bars = fetcher.fetchDaily(stock, LIVE_WARMUP_START);
                    if (bars == null || bars.size() < MIN_BARS) return null;
                    return scanOneStock(market.code(), factory, bars, stock, sector, stratName);
                });
            }
        }

        List<StockSignal> signals = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<StockSignal>> futures = pool.invokeAll(tasks);
            for (Future<StockSignal> f : futures) {
                try {
                    StockSignal s = f.get();
                    if (s != null) signals.add(s);
                } catch (ExecutionException e) {
                    log.debug("Scan task failed: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] stock scan interrupted; returning {} partial signals", market.code(), signals.size());
        } finally {
            pool.shutdownNow();
        }

        log.info("[{}] stock scan complete: {} signals across {} sectors", market.code(), signals.size(), winners.size());
        return signals;
    }

    /**
     * Run the strategy on one stock and detect the signal on the last bar by comparing position
     * state before and after processing it. Ported verbatim from {@code LiveSignalScanner}.
     */
    private static StockSignal scanOneStock(String market, Supplier<Strategy> factory, List<Bar> bars,
                                            String stock, String sector, String stratName) {
        Strategy strategy = factory.get();
        StrategyData data = strategy.computeIndicators(bars);
        double cap = BacktestConfig.INITIAL_CAPITAL;
        TradingViewEngine engine = new TradingViewEngine(
                cap, BacktestConfig.COMMISSION_PCT, BacktestConfig.SLIPPAGE,
                "percent_of_equity", 100.0, 0);

        int lastIdx = bars.size() - 1;
        for (int i = 0; i < lastIdx; i++) {
            Bar bar = bars.get(i);
            LocalDate d = bar.getDate();
            engine.processBar(d, bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
            boolean active = !d.isBefore(TRADE_FROM);
            strategy.onBar(engine, data, i, active);
        }

        boolean hadPosition = engine.getPosition() != null;
        int tradesBeforeLast = engine.getClosedTrades().size();

        Bar lastBar = bars.get(lastIdx);
        LocalDate lastDate = lastBar.getDate();
        engine.processBar(lastDate, lastBar.getOpen(), lastBar.getHigh(),
                lastBar.getLow(), lastBar.getClose(), lastIdx);
        boolean active = !lastDate.isBefore(TRADE_FROM);
        strategy.onBar(engine, data, lastIdx, active);

        boolean hasPosition = engine.getPosition() != null;
        int tradesAfterLast = engine.getClosedTrades().size();
        boolean tradeClosedOnLastBar = tradesAfterLast > tradesBeforeLast;

        // 1-day change from the last two closes (for the "1D" columns in the HOLD/portfolio views).
        Double prevClose = lastIdx >= 1 ? bars.get(lastIdx - 1).getClose() : null;
        Double dayChangePct = (prevClose != null && prevClose != 0)
                ? (lastBar.getClose() - prevClose) / prevClose * 100.0 : null;

        String signalType;
        double price = lastBar.getClose();
        Double stopLoss = null;
        Double takeProfit = null;
        Double entryPrice = null;
        Double unrealizedPnlPct = null;
        LocalDate entryDate = null;
        String comment = null;

        boolean pendingExit = engine.hasPendingExit();
        boolean pendingEntry = engine.hasPendingEntry();
        boolean actionableTomorrow = false;

        if (hadPosition && hasPosition && pendingExit) {
            signalType = "SELL";
            actionableTomorrow = true;
            Position pos = engine.getPosition();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            String exitReason = engine.getPendingExitComment();
            comment = "Execute next session open. Reason: " + (exitReason != null ? exitReason : "Exit");
        } else if (!hadPosition && !hasPosition && pendingEntry) {
            signalType = "BUY";
            actionableTomorrow = true;
            comment = "Execute next session open";
        } else if (!hadPosition && hasPosition) {
            signalType = "BUY";
            Position pos = engine.getPosition();
            price = pos.getEntryPrice();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            comment = "Already filled at session open. " + (pos.getComment() != null ? pos.getComment() : "");
        } else if (hadPosition && !hasPosition) {
            signalType = "SELL";
            if (tradesAfterLast > tradesBeforeLast) {
                ClosedTrade lastTrade = engine.getClosedTrades().get(tradesAfterLast - 1);
                price = lastTrade.getExitPrice();
                comment = "Already filled at session open. Reason: " + lastTrade.getExitComment();
            }
        } else if (hadPosition && hasPosition && tradeClosedOnLastBar) {
            signalType = "BUY";
            actionableTomorrow = pendingEntry;
            Position pos = engine.getPosition();
            price = pos.getEntryPrice();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            comment = "Reversed. " + (pos.getComment() != null ? pos.getComment() : "");
        } else if (hasPosition) {
            signalType = "HOLD";
            Position pos = engine.getPosition();
            stopLoss = pos.getStopLoss();
            takeProfit = pos.getTakeProfit();
            entryPrice = pos.getEntryPrice();
            entryDate = pos.getEntryDate();
            unrealizedPnlPct = ((price - entryPrice) / entryPrice) * 100.0;
        } else {
            signalType = "FLAT";
        }

        return new StockSignal(market, lastDate, sector, stratName, stock.replace(".NS", ""), signalType,
                price, stopLoss, takeProfit, entryPrice, entryDate, unrealizedPnlPct,
                comment, actionableTomorrow, prevClose, dayChangePct);
    }

    // ── Classpath-backed config (winners + refined variants) ──────────────────────────────

    private static String winnersResource(Market market) {
        return market.isUs() ? "stock/sector_winners_us.csv" : "stock/sector_winners.csv";
    }

    private static String refinedResource(Market market) {
        return market.isUs() ? "stock/refined_variants_us.json" : "stock/refined_variants.json";
    }

    /** Reads {@code [sector, strategy]} pairs from the bundled winners CSV on the classpath. */
    private static List<String[]> loadWinners(Market market) {
        List<String[]> out = new ArrayList<>();
        String resource = winnersResource(market);
        try (InputStream is = StockSignalScanner.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                br.readLine(); // header
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",");
                    if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                        out.add(new String[]{parts[0].trim(), parts[1].trim()});
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", resource, e.getMessage());
        }
        return out;
    }

    /**
     * Loads the market's "refined" (RF:) variants from the bundled JSON on the classpath and turns
     * each into a runnable strategy. The US winners are all RF: variants, so without this they would
     * not resolve. India ships no such file (its winners are all registry variants), so an absent
     * resource just yields an empty map. Mirrors {@code RefinedVariants.load} but reads the
     * classpath instead of the filesystem.
     */
    private static Map<String, Supplier<Strategy>> loadRefinedVariants(Market market) {
        Map<String, Supplier<Strategy>> out = new LinkedHashMap<>();
        String resource = refinedResource(market);
        try (InputStream is = StockSignalScanner.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return out;
            Map<String, Map<String, Object>> raw = MAPPER.readValue(
                    is, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
            for (Map.Entry<String, Map<String, Object>> e : raw.entrySet()) {
                Map<String, Object> spec = e.getValue();
                if (spec == null) continue;
                String cls = String.valueOf(spec.getOrDefault("class", "HeikinAshiStrategy"));
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) spec.get("params");
                if (params == null) continue;
                Map<String, Object> p = new HashMap<>(params);
                p.put("variant_name", e.getKey());
                out.put(e.getKey(), VariantRegistry.build(cls, p));
            }
            log.info("[{}] loaded {} refined variants from {}", market.code(), out.size(), resource);
        } catch (Exception e) {
            log.warn("Failed to read {}: {}", resource, e.getMessage());
        }
        return out;
    }
}
