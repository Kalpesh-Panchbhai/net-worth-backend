package com.networth.networthbackend.stock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * My Portfolio CRUD + valuation. Positions are enriched with a current price (the latest stored
 * signal for that stock) so the app can show live-ish invested vs. current value and P&L without a
 * per-request Yahoo call.
 *
 * <pre>
 *   GET    /stock-portfolio?market=in          — positions + valuation summary
 *   POST   /stock-portfolio  {market, stock, sector?, strategy?, entryDate?, entryPrice?, quantity?, notes?}
 *   PUT    /stock-portfolio  {id, ...fields}   — partial edit; only the fields sent are changed
 *                                               (e.g. close a position: status=closed, exitPrice, exitDate)
 *   DELETE /stock-portfolio  {id}
 * </pre>
 */
@Slf4j
public class StockPositionApiHandler extends EntityHandlerBase {

    private final StockPositionRepository positions = new StockPositionRepository();
    private final StockSignalRepository signals = new StockSignalRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context)
            throws Exception {
        String method = request.getHttpMethod() == null ? "GET" : request.getHttpMethod().toUpperCase();
        return switch (method) {
            case "GET" -> list(request);
            case "POST" -> add(request);
            case "PUT", "PATCH" -> update(request);
            case "DELETE" -> remove(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, "Unsupported method: " + method);
        };
    }

    private APIGatewayProxyResponseEvent list(APIGatewayProxyRequestEvent request) throws Exception {
        String market = market(request);
        List<StockPosition> all = positions.findByMarket(market);

        // Current price + previous close per stock from the latest signal snapshot (bare ticker key).
        Map<String, Double> priceByStock = new java.util.HashMap<>();
        Map<String, Double> prevByStock = new java.util.HashMap<>();
        for (StockSignal s : signals.findByMarket(market)) {
            priceByStock.put(s.stock().toUpperCase(), s.price());
            if (s.prevClose() != null) prevByStock.put(s.stock().toUpperCase(), s.prevClose());
        }

        List<ValuedPosition> valued = all.stream().map(p -> value(p, priceByStock, prevByStock)).toList();

        double invested = 0, current = 0, realized = 0, todaysPnl = 0, prevValue = 0;
        int open = 0, closed = 0;
        for (ValuedPosition v : valued) {
            if ("open".equalsIgnoreCase(v.position().status())) {
                open++;
                if (v.invested() != null) invested += v.invested();
                if (v.currentValue() != null) current += v.currentValue();
                if (v.dayPnl() != null) { todaysPnl += v.dayPnl(); prevValue += (v.currentValue() != null ? v.currentValue() : 0) - v.dayPnl(); }
            } else {
                closed++;
                if (v.realizedPnl() != null) realized += v.realizedPnl();
            }
        }
        double unrealized = current - invested;
        Summary summary = new Summary(open, closed, round(invested), round(current),
                round(unrealized), invested > 0 ? round(unrealized / invested * 100) : null, round(realized),
                round(todaysPnl), prevValue > 0 ? round(todaysPnl / prevValue * 100) : null);
        return ok(new PortfolioResponse(market, summary, valued));
    }

    private APIGatewayProxyResponseEvent add(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode b = getJsonBody(request);
        String stock = text(b, "stock");
        if (stock == null || stock.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "stock is required");
        String market = "us".equalsIgnoreCase(text(b, "market")) ? "us" : "in";
        StockPosition p = new StockPosition(
                UUID.randomUUID().toString().substring(0, 12), market, stock.trim(),
                text(b, "sector"), text(b, "strategy"), date(b, "entryDate"), dbl(b, "entryPrice"),
                dbl(b, "quantity"), text(b, "notes"), "open", null, null);
        positions.upsert(p);
        return list(withMarket(request, market));
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode b = getJsonBody(request);
        String id = text(b, "id");
        if (id == null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "id is required");
        StockPosition existing = positions.findById(id);
        if (existing == null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "No position with id " + id);
        // Merge, not replace: the close-position dialog sends only {id, market, stock, status, exitPrice,
        // exitDate}, so a full replace wiped entryPrice/quantity/sector/strategy and with them the
        // realized P&L of the trade that was just closed. Fields absent from the body keep their
        // stored value; a field sent as JSON null is an explicit clear.
        String market = b.hasNonNull("market")
                ? ("us".equalsIgnoreCase(text(b, "market")) ? "us" : "in")
                : existing.market();
        StockPosition p = new StockPosition(id, market,
                merge(b, "stock", existing.stock()),
                merge(b, "sector", existing.sector()),
                merge(b, "strategy", existing.strategy()),
                mergeDate(b, "entryDate", existing.entryDate()),
                mergeDbl(b, "entryPrice", existing.entryPrice()),
                mergeDbl(b, "quantity", existing.quantity()),
                merge(b, "notes", existing.notes()),
                merge(b, "status", existing.status()),
                mergeDbl(b, "exitPrice", existing.exitPrice()),
                mergeDate(b, "exitDate", existing.exitDate()));
        positions.upsert(p);
        return list(withMarket(request, market));
    }

    private APIGatewayProxyResponseEvent remove(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode b = getJsonBody(request);
        String id = text(b, "id");
        if (id == null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "id is required");
        positions.deleteById(id);
        return list(request);
    }

    private static ValuedPosition value(StockPosition p, Map<String, Double> priceByStock, Map<String, Double> prevByStock) {
        String bare = p.stock().toUpperCase().replace(".NS", "");
        Double qty = p.quantity();
        Double entry = p.entryPrice();
        Double invested = (qty != null && entry != null) ? qty * entry : null;
        boolean isOpen = "open".equalsIgnoreCase(p.status());
        Double current = priceByStock.get(bare);
        Double currentValue = (isOpen && qty != null && current != null) ? qty * current : null;
        Double unrealizedPct = (invested != null && currentValue != null && invested != 0)
                ? round((currentValue - invested) / invested * 100) : null;
        Double realized = (!isOpen && qty != null && entry != null && p.exitPrice() != null)
                ? round((p.exitPrice() - entry) * qty) : null;
        Double realizedPct = (!isOpen && entry != null && p.exitPrice() != null && entry != 0)
                ? round((p.exitPrice() - entry) / entry * 100) : null;
        // Today's move for open positions, from the signal's previous close.
        Double prev = prevByStock.get(bare);
        Double dayChangePct = (isOpen && current != null && prev != null && prev != 0)
                ? round((current - prev) / prev * 100) : null;
        Double dayPnl = (isOpen && qty != null && current != null && prev != null) ? round(qty * (current - prev)) : null;
        return new ValuedPosition(p, round(invested), round(currentValue), current, unrealizedPct, realized, realizedPct, dayChangePct, dayPnl);
    }

    private String market(APIGatewayProxyRequestEvent r) {
        return "us".equalsIgnoreCase(queryParam(r, "market")) ? "us" : "in";
    }

    private APIGatewayProxyRequestEvent withMarket(APIGatewayProxyRequestEvent r, String market) {
        Map<String, String> q = new java.util.HashMap<>(r.getQueryStringParameters() == null ? Map.of() : r.getQueryStringParameters());
        q.put("market", market);
        return r.withQueryStringParameters(q);
    }

    private static Double round(Double v) { return v == null ? null : Math.round(v * 100.0) / 100.0; }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String text(JsonNode n, String f) { return n != null && n.hasNonNull(f) ? n.get(f).asText() : null; }
    private static Double dbl(JsonNode n, String f) { return n != null && n.hasNonNull(f) ? n.get(f).asDouble() : null; }

    /**
     * Patch semantics for {@link #update}: field missing from the body keeps {@code current},
     * field present (including an explicit {@code null}) takes the body's value.
     */
    private static boolean sent(JsonNode n, String f) { return n != null && n.has(f); }
    private static String merge(JsonNode n, String f, String current) { return sent(n, f) ? text(n, f) : current; }
    private static Double mergeDbl(JsonNode n, String f, Double current) { return sent(n, f) ? dbl(n, f) : current; }
    private static LocalDate mergeDate(JsonNode n, String f, LocalDate current) { return sent(n, f) ? date(n, f) : current; }
    private static LocalDate date(JsonNode n, String f) {
        String s = text(n, f);
        try { return s == null || s.isBlank() ? null : LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    public record ValuedPosition(StockPosition position, Double invested, Double currentValue,
                                 Double currentPrice, Double unrealizedPct, Double realizedPnl, Double realizedPct,
                                 Double dayChangePct, Double dayPnl) {
    }

    public record Summary(int open, int closed, double invested, double currentValue,
                          double unrealized, Double unrealizedPct, double realized,
                          double todaysPnl, Double todaysPnlPct) {
    }

    public record PortfolioResponse(String market, Summary summary, List<ValuedPosition> positions) {
    }
}
