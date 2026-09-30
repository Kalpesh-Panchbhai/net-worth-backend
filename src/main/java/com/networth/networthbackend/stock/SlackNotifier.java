package com.networth.networthbackend.stock;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Posts the day's actionable BUY/SELL signals to Slack, one message per market. Ported from the
 * stock-analyzer Python UI's {@code _fire_slack_alert}: BUY signals are always shown; SELL signals
 * are limited to owned stocks (so you're only warned about positions you actually hold). Rendered as
 * a monospace code-block table with TradingView chart links.
 *
 * <p>Webhooks come from env: {@code SLACK_WEBHOOK_URL_IN} / {@code SLACK_WEBHOOK_URL_US}. When the
 * relevant var is unset the notifier is a no-op, so Slack is entirely optional.
 */
@Slf4j
public class SlackNotifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String TV_BASE = "https://www.tradingview.com/chart/?symbol=";

    /** Sends the alert for one market. Never throws — a Slack failure must not fail the daily job. */
    public void notify(String market, List<StockSignal> signals, Set<String> heldStocks,
                       Map<String, StockPerformance> perfByStock, Instant lastScan) {
        try {
            String webhook = webhookFor(market);
            if (webhook == null || webhook.isBlank()) {
                log.info("[{}] Slack webhook not configured; skipping alert", market);
                return;
            }
            String message = buildMessage(market, signals, heldStocks, perfByStock, lastScan);
            boolean ok = send(webhook, message);
            log.info("[{}] Slack alert: {}", market, ok ? "sent" : "failed");
        } catch (Exception e) {
            log.warn("[{}] Slack alert error: {}", market, e.getMessage());
        }
    }

    private static String webhookFor(String market) {
        return "us".equalsIgnoreCase(market)
                ? System.getenv("SLACK_WEBHOOK_URL_US")
                : System.getenv("SLACK_WEBHOOK_URL_IN");
    }

    /**
     * Builds the Slack text: BUY block then SELL block (owned only), each sorted by ticker, rendered
     * as a fixed-width table inside a code block so columns line up in Slack.
     */
    static String buildMessage(String market, List<StockSignal> signals, Set<String> heldStocks,
                               Map<String, StockPerformance> perfByStock, Instant lastScan) {
        boolean us = "us".equalsIgnoreCase(market);
        String label = us ? "US" : "IN";
        String cur = us ? "$" : "Rs";

        List<StockSignal> buys = new ArrayList<>();
        List<StockSignal> sells = new ArrayList<>();
        for (StockSignal s : signals) {
            if (!s.actionableTomorrow()) continue;
            if ("BUY".equalsIgnoreCase(s.type())) {
                buys.add(s);
            } else if ("SELL".equalsIgnoreCase(s.type())) {
                // SELL only for owned stocks. When the held set is null we can't tell, so show all.
                if (heldStocks == null || heldStocks.contains(s.stock().toUpperCase())) sells.add(s);
            }
        }
        Comparator<StockSignal> byStock = Comparator.comparing(s -> s.stock().toUpperCase());
        buys.sort(byStock);
        sells.sort(byStock);

        List<StockSignal> ordered = new ArrayList<>(buys);
        ordered.addAll(sells);

        String date = signals.isEmpty() || signals.get(0).dataDate() == null ? "" : signals.get(0).dataDate().toString();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 *Signal Scanner — %s*  ·  %s%n", label, date));
        sb.append(String.format("_prices in %s  ·  %d BUY · %d SELL_%n", cur, buys.size(), sells.size()));

        if (ordered.isEmpty()) {
            sb.append("\nNo actionable BUY/SELL signals.");
            appendLastScan(sb, lastScan);
            return sb.toString();
        }

        sb.append(renderTable(ordered, perfByStock == null ? Map.of() : perfByStock));

        List<String> links = new ArrayList<>();
        for (StockSignal s : ordered) {
            links.add("<" + tvUrl(s.stock(), market) + "|" + s.stock() + ">");
        }
        sb.append("\n📈 *Charts:* ").append(String.join("  ·  ", links));
        appendLastScan(sb, lastScan);
        return sb.toString();
    }

    private static final DateTimeFormatter SCAN_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private static void appendLastScan(StringBuilder sb, Instant lastScan) {
        if (lastScan == null) return;
        sb.append("\n_Last scan: ").append(SCAN_FMT.format(lastScan.atZone(IST))).append(" IST_");
    }

    private static final String[] HEADERS = {"SIDE", "STOCK", "SECTOR", "PRICE", "SL", "TP",
            "WR%", "PF", "CAGR", "AVG P&L", "AVG MAXDD", "AVG RUN-UP", "AVG HOLD"};
    private static final int LEFT_ALIGN_COLS = 3;

    private static String renderTable(List<StockSignal> rows, Map<String, StockPerformance> perfByStock) {
        List<String[]> cells = new ArrayList<>();
        for (StockSignal s : rows) {
            // Per-stock backtest stats of the strategy behind the signal; "-" when never backtested.
            StockPerformance p = perfByStock.get(s.stock().toUpperCase().replace(".NS", ""));
            cells.add(new String[]{
                    s.type().toUpperCase(),
                    s.stock(),
                    shortSector(s.sector()),
                    num(s.price()),
                    numN(s.stopLoss()),
                    numN(s.takeProfit()),
                    p == null || p.wr() == null ? "-" : String.format(Locale.US, "%.0f%%", p.wr()),
                    p == null ? "-" : profitFactor(p),
                    p == null || p.cagr() == null ? "-" : String.format(Locale.US, "%.1f%%", p.cagr()),
                    p == null ? "-" : signedPct(p.avgTradePct()),
                    p == null ? "-" : signedPct(p.avgMaePct()),
                    p == null ? "-" : signedPct(p.avgMfePct()),
                    p == null || p.avgHoldDays() == null ? "-" : String.format(Locale.US, "%.0fd", p.avgHoldDays()),
            });
        }
        int ncol = HEADERS.length;
        int[] widths = new int[ncol];
        for (int i = 0; i < ncol; i++) widths[i] = HEADERS[i].length();
        for (String[] r : cells) {
            for (int i = 0; i < ncol; i++) widths[i] = Math.max(widths[i], r[i].length());
        }

        StringBuilder sb = new StringBuilder("```\n");
        sb.append(sep(widths, '┌', '┬', '┐')).append('\n');
        sb.append(rowLine(HEADERS, widths)).append('\n');
        sb.append(sep(widths, '├', '┼', '┤')).append('\n');
        for (int r = 0; r < cells.size(); r++) {
            sb.append(rowLine(cells.get(r), widths)).append('\n');
            sb.append(r < cells.size() - 1 ? sep(widths, '├', '┼', '┤') : sep(widths, '└', '┴', '┘')).append('\n');
        }
        sb.append("```");
        return sb.toString();
    }

    private static String sep(int[] widths, char l, char m, char r) {
        StringBuilder sb = new StringBuilder().append(l);
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            sb.append(i < widths.length - 1 ? m : r);
        }
        return sb.toString();
    }

    private static String rowLine(String[] vals, int[] widths) {
        StringBuilder sb = new StringBuilder().append('│');
        for (int i = 0; i < widths.length; i++) {
            String v = i < vals.length ? vals[i] : "";
            String padded = i < LEFT_ALIGN_COLS ? padRight(v, widths[i]) : padLeft(v, widths[i]);
            sb.append(' ').append(padded).append(' ').append('│');
        }
        return sb.toString();
    }

    private static String padRight(String s, int w) { return s + " ".repeat(Math.max(0, w - s.length())); }
    private static String padLeft(String s, int w) { return " ".repeat(Math.max(0, w - s.length())) + s; }

    private static String num(double v) { return String.format(Locale.US, "%.2f", v); }
    private static String numN(Double v) { return v == null ? "-" : String.format(Locale.US, "%.2f", v); }
    private static String signedPct(Double v) { return v == null ? "-" : String.format(Locale.US, "%+.2f%%", v); }

    /** A backtest with winners and no losers has an unbounded profit factor; show it as "inf". */
    private static String profitFactor(StockPerformance p) {
        if (p.pf() != null && !p.pf().isInfinite()) return String.format(Locale.US, "%.2f", p.pf());
        boolean noLosses = p.losers() != null && p.losers() == 0 && p.winners() != null && p.winners() > 0;
        return p.pf() != null || noLosses ? "inf" : "-";
    }

    /** Compact sector label: first letters of each word, e.g. "financial_services" → "FS". */
    private static String shortSector(String sector) {
        if (sector == null || sector.isBlank()) return "-";
        StringBuilder sb = new StringBuilder();
        for (String part : sector.split("_")) {
            if (!part.isBlank()) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }

    private static String tvUrl(String stock, String market) {
        String exchange = "us".equalsIgnoreCase(market) ? "NASDAQ" : "NSE";
        String symbol = exchange + ":" + stock;
        return TV_BASE + java.net.URLEncoder.encode(symbol, java.nio.charset.StandardCharsets.UTF_8);
    }

    private boolean send(String webhook, String text) {
        try {
            String payload = com.networth.networthbackend.common.Jsons.MAPPER
                    .createObjectNode().put("text", text).toString();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() / 100 == 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Slack send failed: {}", e.getMessage());
            return false;
        }
    }
}
