package com.networth.networthbackend.stock;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    private static final String TV_BASE = "https://www.tradingview.com/chart/?symbol=";

    /** Sends the alert for one market. Never throws — a Slack failure must not fail the daily job. */
    public void notify(String market, List<StockSignal> signals, Set<String> heldStocks) {
        try {
            String webhook = webhookFor(market);
            if (webhook == null || webhook.isBlank()) {
                log.info("[{}] Slack webhook not configured; skipping alert", market);
                return;
            }
            String message = buildMessage(market, signals, heldStocks);
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
    static String buildMessage(String market, List<StockSignal> signals, Set<String> heldStocks) {
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
            return sb.toString();
        }

        sb.append(renderTable(ordered));

        List<String> links = new ArrayList<>();
        for (StockSignal s : ordered) {
            links.add("<" + tvUrl(s.stock(), market) + "|" + s.stock() + ">");
        }
        sb.append("\n📈 *Charts:* ").append(String.join("  ·  ", links));
        return sb.toString();
    }

    private static final String[] HEADERS = {"SIDE", "STOCK", "SECTOR", "PRICE", "SL", "TP"};
    private static final int LEFT_ALIGN_COLS = 3;

    private static String renderTable(List<StockSignal> rows) {
        List<String[]> cells = new ArrayList<>();
        for (StockSignal s : rows) {
            cells.add(new String[]{
                    s.type().toUpperCase(),
                    s.stock(),
                    shortSector(s.sector()),
                    num(s.price()),
                    numN(s.stopLoss()),
                    numN(s.takeProfit()),
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
