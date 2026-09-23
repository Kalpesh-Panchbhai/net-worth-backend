package com.networth.networthbackend.refresh;

import com.networth.networthbackend.meta.AppMetaRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * UI-configurable schedule for the daily data refresh. The EventBridge rule fires the scheduler
 * every hour; this class decides whether a given tick should actually run the job, based on the
 * user's chosen interval, weekend policy and enabled flag (stored in {@code app_meta}). Interval
 * cadence is enforced against the last successful refresh, so changing the interval takes effect
 * immediately without touching AWS.
 */
@Slf4j
public class RefreshSchedule {

    public static final String KEY_INTERVAL_HOURS = "refresh_interval_hours";
    public static final String KEY_WEEKDAYS_ONLY = "refresh_weekdays_only";
    public static final String KEY_ENABLED = "refresh_enabled";

    private static final int DEFAULT_INTERVAL_HOURS = 8;
    /** Allowed interval choices surfaced in the UI. */
    public static final int[] ALLOWED_INTERVALS = {1, 2, 4, 8, 12, 24};
    /** Slack so an hourly tick a few seconds early still counts as "due". */
    private static final long DUE_BUFFER_MS = 10 * 60_000L;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AppMetaRepository meta;

    public RefreshSchedule() {
        this(new AppMetaRepository());
    }

    public RefreshSchedule(AppMetaRepository meta) {
        this.meta = meta;
    }

    public record Config(int intervalHours, boolean weekdaysOnly, boolean enabled) {
    }

    public Config get() {
        try {
            int interval = parseInt(meta.get(KEY_INTERVAL_HOURS), DEFAULT_INTERVAL_HOURS);
            if (!isAllowed(interval)) interval = DEFAULT_INTERVAL_HOURS;
            boolean weekdaysOnly = "true".equalsIgnoreCase(meta.get(KEY_WEEKDAYS_ONLY));
            String en = meta.get(KEY_ENABLED);
            boolean enabled = en == null || "true".equalsIgnoreCase(en); // default enabled
            return new Config(interval, weekdaysOnly, enabled);
        } catch (Exception e) {
            log.warn("Reading refresh schedule failed; using defaults: {}", e.getMessage());
            return new Config(DEFAULT_INTERVAL_HOURS, false, true);
        }
    }

    public void save(int intervalHours, boolean weekdaysOnly, boolean enabled) throws Exception {
        int interval = isAllowed(intervalHours) ? intervalHours : DEFAULT_INTERVAL_HOURS;
        meta.set(KEY_INTERVAL_HOURS, String.valueOf(interval));
        meta.set(KEY_WEEKDAYS_ONLY, String.valueOf(weekdaysOnly));
        meta.set(KEY_ENABLED, String.valueOf(enabled));
    }

    /** Whether the job should run right now (called from each hourly scheduler tick). */
    public boolean isDue(long nowMillis) {
        Config cfg = get();
        if (!cfg.enabled()) return false;
        if (cfg.weekdaysOnly() && isWeekend(nowMillis)) return false;
        long last = lastRefreshMillis();
        if (last <= 0) return true; // never refreshed → run now
        long intervalMs = cfg.intervalHours() * 3600_000L;
        return (nowMillis - last) >= (intervalMs - DUE_BUFFER_MS);
    }

    /** Epoch millis of the next run (best-effort, for display). Null when disabled. */
    public Long nextRunAt(long nowMillis) {
        Config cfg = get();
        if (!cfg.enabled()) return null;
        long intervalMs = cfg.intervalHours() * 3600_000L;
        long last = lastRefreshMillis();
        long candidate = last <= 0 ? nowMillis : Math.max(nowMillis, last + intervalMs);
        if (cfg.weekdaysOnly()) {
            ZonedDateTime z = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(candidate), IST);
            while (z.getDayOfWeek() == DayOfWeek.SATURDAY || z.getDayOfWeek() == DayOfWeek.SUNDAY) {
                z = z.plusDays(1);
            }
            candidate = z.toInstant().toEpochMilli();
        }
        return candidate;
    }

    private long lastRefreshMillis() {
        try {
            String s = meta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
            return s == null ? 0 : Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isWeekend(long millis) {
        DayOfWeek d = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), IST).getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private static boolean isAllowed(int v) {
        for (int a : ALLOWED_INTERVALS) if (a == v) return true;
        return false;
    }

    private static int parseInt(String s, int dflt) {
        try {
            return s == null ? dflt : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
