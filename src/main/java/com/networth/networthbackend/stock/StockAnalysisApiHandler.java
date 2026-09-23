package com.networth.networthbackend.stock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only stock analyzer API. Serves everything the ported UI needs from the precomputed tables:
 * live signals (enriched with backtest stats), sector winners, per-sector and per-stock backtest
 * performance, trade history, and trade-based equity curves. Every read is a bounded indexed query,
 * so it answers well inside API Gateway's 29-second timeout.
 *
 * <pre>
 *   GET /stock-analysis?action=signals&market=in[&type=BUY]
 *   GET /stock-analysis?action=winners&market=in
 *   GET /stock-analysis?action=sector&market=in&sector=healthcare
 *   GET /stock-analysis?action=stock&market=in&stock=TCS
 *   GET /stock-analysis?action=trades&market=in&sector=healthcare        (or &stock=TCS)
 *   GET /stock-analysis?action=equity&market=in[&sector=..|&stock=..]
 * </pre>
 */
@Slf4j
public class StockAnalysisApiHandler extends EntityHandlerBase {

    private static final double INITIAL_CAPITAL = 100_000.0;

    private final StockSignalRepository signals = new StockSignalRepository();
    private final StockPerformanceRepository performance = new StockPerformanceRepository();
    private final StockTradeRepository trades = new StockTradeRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        String resolved = (action == null || action.equalsIgnoreCase(Constants.Actions.GET)) ? "signals" : action.toLowerCase();
        String market = "us".equalsIgnoreCase(queryParam(request, "market")) ? "us" : "in";
        return switch (resolved) {
            case "signals" -> signals(request, market);
            case "winners" -> winners(market);
            case "sector" -> sector(request, market);
            case "stock" -> stock(request, market);
            case "trades" -> tradesList(request, market);
            case "closed" -> ok(new ClosedResponse(market, trades.findClosedByMarket(market)));
            case "equity" -> equity(request, market);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                    String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "stock-analysis", action));
        };
    }

    // ── Signals (enriched with per-stock backtest stats) ──────────────────────
    private APIGatewayProxyResponseEvent signals(APIGatewayProxyRequestEvent request, String market) throws Exception {
        List<StockSignal> all = signals.findByMarket(market);
        Map<String, StockPerformance> perf = performance.byBareStock(market);

        String typeFilter = queryParam(request, "type");
        Map<String, Long> counts = new TreeMap<>();
        for (StockSignal s : all) counts.merge(s.type(), 1L, Long::sum);

        List<EnrichedSignal> enriched = new ArrayList<>();
        for (StockSignal s : all) {
            if (typeFilter != null && !typeFilter.isBlank() && !typeFilter.equalsIgnoreCase(s.type())) continue;
            StockPerformance p = perf.get(s.stock().toUpperCase());
            enriched.add(new EnrichedSignal(s,
                    p == null ? null : p.wr(), p == null ? null : p.pf(), p == null ? null : p.cagr(),
                    p == null ? null : p.avgTradePct(), p == null ? null : p.avgMaePct(),
                    p == null ? null : p.avgMfePct(), p == null ? null : p.avgHoldDays()));
        }
        return ok(new SignalsResponse(market, signals.latestDataDate(market), all.size(), counts, enriched));
    }

    // ── Winners: per-sector aggregates + composite/strategy from the bundled winners CSV ──────
    private APIGatewayProxyResponseEvent winners(String market) throws Exception {
        Map<String, String[]> csv = loadWinnersCsv(market); // sector -> [strategy, composite, stocksUsed]
        Map<String, List<StockPerformance>> bySector = new LinkedHashMap<>();
        for (StockPerformance p : performance.findByMarket(market)) {
            bySector.computeIfAbsent(p.sector(), k -> new ArrayList<>()).add(p);
        }

        List<WinnerRow> rows = new ArrayList<>();
        double portInvested = 0, portFinal = 0;
        long totalTradesAll = 0;
        double years = tradingYears();
        for (var e : bySector.entrySet()) {
            List<StockPerformance> ps = e.getValue();
            int n = ps.size();
            double sumPf = 0, sumWr = 0, sumTradePct = 0, sumMae = 0, sumMfe = 0, finalEq = 0;
            long tradeCount = 0;
            int pfN = 0, wrN = 0, tpN = 0, maeN = 0, mfeN = 0;
            for (StockPerformance p : ps) {
                if (p.pf() != null) { sumPf += p.pf(); pfN++; }
                if (p.wr() != null) { sumWr += p.wr(); wrN++; }
                if (p.avgTradePct() != null) { sumTradePct += p.avgTradePct(); tpN++; }
                if (p.avgMaePct() != null) { sumMae += p.avgMaePct(); maeN++; }
                if (p.avgMfePct() != null) { sumMfe += p.avgMfePct(); mfeN++; }
                if (p.trades() != null) tradeCount += p.trades();
                finalEq += p.finalEquity() != null ? p.finalEquity() : INITIAL_CAPITAL;
            }
            double invested = n * INITIAL_CAPITAL;
            double portfolioCagr = years > 0 && invested > 0 ? (Math.pow(finalEq / invested, 1.0 / years) - 1) * 100 : 0;
            portInvested += invested; portFinal += finalEq; totalTradesAll += tradeCount;

            String[] meta = csv.getOrDefault(e.getKey(), new String[]{ps.get(0).strategy(), null, String.valueOf(n)});
            rows.add(new WinnerRow(e.getKey(), meta[0],
                    round(portfolioCagr), avg(sumPf, pfN), avg(sumWr, wrN), avg(sumTradePct, tpN),
                    avg(sumMae, maeN), avg(sumMfe, mfeN), tradeCount,
                    meta[1] == null ? null : parseD(meta[1]), n));
        }
        double overallCagr = years > 0 && portInvested > 0 ? round((Math.pow(portFinal / portInvested, 1.0 / years) - 1) * 100) : 0;
        WinnersAggregate agg = new WinnersAggregate(round(portInvested), round(portFinal), overallCagr, totalTradesAll, rows.size());
        return ok(new WinnersResponse(market, agg, rows));
    }

    // ── Sector detail: per-stock performance ──────────────────────────────────
    private APIGatewayProxyResponseEvent sector(APIGatewayProxyRequestEvent request, String market) throws Exception {
        String sector = queryParam(request, "sector");
        if (sector == null || sector.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "sector is required");
        return ok(new SectorResponse(market, sector, performance.findBySector(market, sector)));
    }

    // ── Stock detail: trades + performance ─────────────────────────────────────
    private APIGatewayProxyResponseEvent stock(APIGatewayProxyRequestEvent request, String market) throws Exception {
        String stock = queryParam(request, "stock");
        if (stock == null || stock.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "stock is required");
        String bare = stock.toUpperCase().replace(".NS", "");
        StockPerformance perf = performance.byBareStock(market).get(bare);
        // trades stored with bare stock (importer strips .NS for signals, but trades keep original) — try both.
        List<StockTrade> t = trades.findByStock(market, bare);
        if (t.isEmpty()) t = trades.findByStock(market, bare + ".NS");
        return ok(new StockResponse(market, bare, perf, t));
    }

    private APIGatewayProxyResponseEvent tradesList(APIGatewayProxyRequestEvent request, String market) throws Exception {
        String stock = queryParam(request, "stock");
        String sector = queryParam(request, "sector");
        List<StockTrade> t;
        if (stock != null && !stock.isBlank()) {
            String bare = stock.toUpperCase().replace(".NS", "");
            t = trades.findByStock(market, bare);
            if (t.isEmpty()) t = trades.findByStock(market, bare + ".NS");
        } else if (sector != null && !sector.isBlank()) {
            t = trades.findBySector(market, sector);
        } else {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "sector or stock is required");
        }
        return ok(t);
    }

    // ── Equity curve (trade-based cumulative realized PnL) ─────────────────────
    private APIGatewayProxyResponseEvent equity(APIGatewayProxyRequestEvent request, String market) throws Exception {
        String stock = queryParam(request, "stock");
        String sector = queryParam(request, "sector");
        List<StockTrade> t;
        if (stock != null && !stock.isBlank()) {
            String bare = stock.toUpperCase().replace(".NS", "");
            t = trades.findByStock(market, bare);
            if (t.isEmpty()) t = trades.findByStock(market, bare + ".NS");
        } else if (sector != null && !sector.isBlank()) {
            t = trades.findBySector(market, sector);
        } else {
            // Portfolio-wide: all trades for the market.
            List<StockPerformance> perfRows = performance.findByMarket(market);
            t = new ArrayList<>();
            java.util.Set<String> sectors = new java.util.LinkedHashSet<>();
            for (StockPerformance p : perfRows) sectors.add(p.sector());
            for (String sec : sectors) t.addAll(trades.findBySector(market, sec));
        }
        // Cumulative realized PnL over closed trades, ordered by exit date.
        t.sort((a, b) -> {
            LocalDate ax = a.exitDate(), bx = b.exitDate();
            if (ax == null) return 1; if (bx == null) return -1;
            return ax.compareTo(bx);
        });
        TreeMap<LocalDate, Double> byDate = new TreeMap<>();
        double eq = INITIAL_CAPITAL;
        for (StockTrade tr : t) {
            if (tr.exitDate() == null || tr.pnl() == null) continue;
            eq += tr.pnl();
            byDate.put(tr.exitDate(), eq);
        }
        List<EquityPoint> points = new ArrayList<>();
        for (var e : byDate.entrySet()) points.add(new EquityPoint(e.getKey(), round(e.getValue())));
        return ok(new EquityResponse(market, points));
    }

    // ── winners CSV (classpath) ────────────────────────────────────────────────
    private static Map<String, String[]> loadWinnersCsv(String market) {
        Map<String, String[]> out = new LinkedHashMap<>();
        String resource = "us".equals(market) ? "stock/sector_winners_us.csv" : "stock/sector_winners.csv";
        try (InputStream is = StockAnalysisApiHandler.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return out;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] c = line.split(",");
                    // Sector,Strategy,Composite,...,StocksUsed(last)
                    if (c.length >= 3) {
                        String stocksUsed = c[c.length - 1].trim();
                        out.put(c[0].trim(), new String[]{c[1].trim(), c[2].trim(), stocksUsed});
                    }
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static double tradingYears() {
        int start = 2012; // OOS_TRADE_FROM
        return LocalDate.now().getYear() - start + (LocalDate.now().getDayOfYear() / 365.0);
    }

    private static Double avg(double sum, int n) { return n == 0 ? null : round(sum / n); }
    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
    private static Double parseD(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return null; } }

    // ── response DTOs ───────────────────────────────────────────────────────────
    public record EnrichedSignal(StockSignal signal, Double btWr, Double btPf, Double btCagr,
                                 Double btAvgPnl, Double btAvgMae, Double btAvgMfe, Double btAvgHoldDays) {
    }

    public record SignalsResponse(String market, LocalDate asOf, int total,
                                  Map<String, Long> counts, List<EnrichedSignal> signals) {
    }

    public record WinnerRow(String sector, String strategy, Double portfolioCagr, Double avgPf, Double avgWr,
                            Double avgTradePct, Double avgMaePct, Double avgMfePct, long totalTrades,
                            Double composite, int stocksUsed) {
    }

    public record WinnersAggregate(double invested, double currentValue, double overallCagr,
                                   long totalTrades, int sectors) {
    }

    public record WinnersResponse(String market, WinnersAggregate aggregate, List<WinnerRow> rows) {
    }

    public record SectorResponse(String market, String sector, List<StockPerformance> stocks) {
    }

    public record StockResponse(String market, String stock, StockPerformance performance, List<StockTrade> trades) {
    }

    public record EquityPoint(LocalDate date, double equity) {
    }

    public record EquityResponse(String market, List<EquityPoint> points) {
    }

    public record ClosedResponse(String market, List<StockTrade> trades) {
    }
}
