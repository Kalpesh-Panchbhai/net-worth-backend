package com.networth.networthbackend.stock;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fires each market's Slack alert at its UI-configured time. Invoked frequently (every few minutes)
 * by the alert Lambda; for each market it checks whether "now" (in the market's timezone) has
 * reached the configured hour:minute today and, if so and not already sent today, posts the alert
 * from the latest stored signals. This decouples alert timing from the fixed 8-hourly signal scan.
 */
@Slf4j
public class StockAlertService {

    /**
     * How long after the configured time an alert may still fire. Must exceed the invoke cadence so
     * a due alert is never missed; the {@code last_sent_date} guard prevents duplicates in the window.
     */
    private static final int WINDOW_MINUTES = 30;

    private final StockAlertConfigRepository configRepo;
    private final StockSignalRepository signalRepo;
    private final StockPositionRepository positionRepo;
    private final StockPerformanceRepository performanceRepo;
    private final SlackNotifier slack;

    public StockAlertService() {
        this(new StockAlertConfigRepository(), new StockSignalRepository(), new StockPositionRepository(),
                new StockPerformanceRepository(), new SlackNotifier());
    }

    public StockAlertService(StockAlertConfigRepository configRepo, StockSignalRepository signalRepo,
                             StockPositionRepository positionRepo, StockPerformanceRepository performanceRepo,
                             SlackNotifier slack) {
        this.configRepo = configRepo;
        this.signalRepo = signalRepo;
        this.positionRepo = positionRepo;
        this.performanceRepo = performanceRepo;
        this.slack = slack;
    }

    /** Sends any alerts that are due right now (window-based). Never throws. */
    public void dispatchDue() {
        try {
            for (StockAlertConfig cfg : configRepo.findAllEnsuringDefaults()) {
                try {
                    maybeSend(cfg);
                } catch (Exception e) {
                    log.warn("[{}] alert dispatch failed: {}", cfg.market(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Alert dispatch failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Fires one market's alert now — used when a dedicated per-market EventBridge rule triggers at
     * the configured time. Timing is guaranteed by the rule, so this only checks enabled / weekday /
     * not-already-sent-today. Never throws.
     */
    public void dispatchMarket(String market) {
        try {
            StockAlertConfig cfg = configRepo.findAllEnsuringDefaults().stream()
                    .filter(c -> c.market().equalsIgnoreCase(market)).findFirst().orElse(null);
            if (cfg == null) { log.warn("No alert config for market {}", market); return; }
            if (!cfg.enabled()) return;
            ZonedDateTime now = ZonedDateTime.now(safeZone(cfg.timezone()));
            if (cfg.weekdaysOnly() && (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY)) return;
            LocalDate today = now.toLocalDate();
            if (today.equals(cfg.lastSentDate())) return; // idempotent within the day
            send(cfg, today);
        } catch (Exception e) {
            log.error("[{}] alert dispatch failed: {}", market, e.getMessage(), e);
        }
    }

    private void maybeSend(StockAlertConfig cfg) throws Exception {
        if (!cfg.enabled()) return;
        ZoneId zone = safeZone(cfg.timezone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        if (cfg.weekdaysOnly() && (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY)) return;

        LocalDate today = now.toLocalDate();
        if (today.equals(cfg.lastSentDate())) return; // already sent today

        int nowMin = now.getHour() * 60 + now.getMinute();
        int cfgMin = cfg.hour() * 60 + cfg.minute();
        if (nowMin < cfgMin || nowMin >= cfgMin + WINDOW_MINUTES) return; // not in the fire window
        send(cfg, today);
    }

    /** Posts the market's digest and stamps last-sent. Skips (without stamping) when no signals exist. */
    private void send(StockAlertConfig cfg, LocalDate today) throws Exception {
        List<StockSignal> signals = signalRepo.findByMarket(cfg.market());
        if (signals.isEmpty()) {
            log.warn("[{}] alert due but no signals stored yet; skipping", cfg.market());
            return; // don't stamp — retry next time
        }
        Set<String> held = positionRepo.heldStocks(cfg.market());
        // Backtest stats and scan time only enrich the message; a failure to load them must not block the alert.
        Map<String, StockPerformance> perf = Map.of();
        Instant lastScan = null;
        try {
            perf = performanceRepo.byBareStock(cfg.market());
            lastScan = signalRepo.lastScanAt(cfg.market());
        } catch (Exception e) {
            log.warn("[{}] could not load backtest stats for the alert: {}", cfg.market(), e.getMessage());
        }
        slack.notify(cfg.market(), signals, held, perf, lastScan);
        configRepo.markSent(cfg.market(), today);
        log.info("[{}] Slack alert fired at {}:{} {}", cfg.market(),
                String.format("%02d", cfg.hour()), String.format("%02d", cfg.minute()), cfg.timezone());
    }

    private static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? "Asia/Kolkata" : tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }
}
