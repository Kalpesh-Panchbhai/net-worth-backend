package com.networth.networthbackend.stock;

import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
    private final SlackNotifier slack;

    public StockAlertService() {
        this(new StockAlertConfigRepository(), new StockSignalRepository(), new StockPositionRepository(), new SlackNotifier());
    }

    public StockAlertService(StockAlertConfigRepository configRepo, StockSignalRepository signalRepo,
                             StockPositionRepository positionRepo, SlackNotifier slack) {
        this.configRepo = configRepo;
        this.signalRepo = signalRepo;
        this.positionRepo = positionRepo;
        this.slack = slack;
    }

    /** Sends any alerts that are due right now. Never throws. */
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

        List<StockSignal> signals = signalRepo.findByMarket(cfg.market());
        if (signals.isEmpty()) {
            log.warn("[{}] alert due but no signals stored yet; will retry next tick", cfg.market());
            return; // don't stamp — retry when signals exist
        }
        Set<String> held = positionRepo.heldStocks(cfg.market());
        slack.notify(cfg.market(), signals, held);
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
