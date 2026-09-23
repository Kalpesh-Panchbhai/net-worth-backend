package com.networth.networthbackend.refresh;

import com.networth.networthbackend.meta.AppMetaRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * UI-configurable schedule for the daily data refresh. The EventBridge rule fires the scheduler
 * every 15 minutes; this class decides whether a given tick should run, based on the user's chosen
 * interval, <b>anchor start time</b>, weekend policy and enabled flag (stored in {@code app_meta}).
 *
 * <p>Runs occur at {@code anchor + k·interval} each day (e.g. every 2h from 01:30 → 01:30, 03:30,
 * 05:30 …). All allowed intervals divide 24h, so the daily slot set is stable. Changing any setting
 * takes effect immediately without touching AWS.
 */
@Slf4j
public class RefreshSchedule {

    public static final String KEY_INTERVAL_HOURS = "refresh_interval_hours";
    public static final String KEY_ANCHOR_HOUR = "refresh_anchor_hour";
    public static final String KEY_ANCHOR_MINUTE = "refresh_anchor_minute";
    public static final String KEY_TIMEZONE = "refresh_timezone";
    public static final String KEY_WEEKDAYS_ONLY = "refresh_weekdays_only";
    public static final String KEY_ENABLED = "refresh_enabled";

    private static final int DEFAULT_INTERVAL_HOURS = 8;
    private static final int DEFAULT_ANCHOR_HOUR = 6;   // 06:00 IST default (matches prior 06/14/22)
    private static final int DEFAULT_ANCHOR_MINUTE = 0;
    private static final String DEFAULT_TZ = "Asia/Kolkata";
    public static final int[] ALLOWED_INTERVALS = {1, 2, 4, 8, 12, 24};

    /** A tick within this many minutes after a slot counts as that slot firing (covers the cadence). */
    private static final long DUE_WINDOW_MS = 20 * 60_000L;

    private final AppMetaRepository meta;

    public RefreshSchedule() {
        this(new AppMetaRepository());
    }

    public RefreshSchedule(AppMetaRepository meta) {
        this.meta = meta;
    }

    public record Config(int intervalHours, int anchorHour, int anchorMinute, String timezone,
                         boolean weekdaysOnly, boolean enabled) {
    }

    public Config get() {
        try {
            int interval = parseInt(meta.get(KEY_INTERVAL_HOURS), DEFAULT_INTERVAL_HOURS);
            if (!isAllowed(interval)) interval = DEFAULT_INTERVAL_HOURS;
            int ah = clamp(parseInt(meta.get(KEY_ANCHOR_HOUR), DEFAULT_ANCHOR_HOUR), 0, 23);
            int am = clamp(parseInt(meta.get(KEY_ANCHOR_MINUTE), DEFAULT_ANCHOR_MINUTE), 0, 59);
            String tz = orDefault(meta.get(KEY_TIMEZONE), DEFAULT_TZ);
            boolean weekdaysOnly = "true".equalsIgnoreCase(meta.get(KEY_WEEKDAYS_ONLY));
            String en = meta.get(KEY_ENABLED);
            boolean enabled = en == null || "true".equalsIgnoreCase(en);
            return new Config(interval, ah, am, tz, weekdaysOnly, enabled);
        } catch (Exception e) {
            log.warn("Reading refresh schedule failed; using defaults: {}", e.getMessage());
            return new Config(DEFAULT_INTERVAL_HOURS, DEFAULT_ANCHOR_HOUR, DEFAULT_ANCHOR_MINUTE, DEFAULT_TZ, false, true);
        }
    }

    public void save(int intervalHours, int anchorHour, int anchorMinute, String timezone,
                     boolean weekdaysOnly, boolean enabled) throws Exception {
        int interval = isAllowed(intervalHours) ? intervalHours : DEFAULT_INTERVAL_HOURS;
        meta.set(KEY_INTERVAL_HOURS, String.valueOf(interval));
        meta.set(KEY_ANCHOR_HOUR, String.valueOf(clamp(anchorHour, 0, 23)));
        meta.set(KEY_ANCHOR_MINUTE, String.valueOf(clamp(anchorMinute, 0, 59)));
        meta.set(KEY_TIMEZONE, safeZone(timezone).getId());
        meta.set(KEY_WEEKDAYS_ONLY, String.valueOf(weekdaysOnly));
        meta.set(KEY_ENABLED, String.valueOf(enabled));
    }

    /** Whether the job should run at this tick: within the window after a slot, not already run. */
    public boolean isDue(long nowMillis) {
        Config cfg = get();
        if (!cfg.enabled()) return false;
        long slot = mostRecentSlot(cfg, nowMillis);
        if (slot < 0) return false;
        if (nowMillis - slot > DUE_WINDOW_MS) return false;         // tick isn't near a slot
        if (lastRefreshMillis() >= slot) return false;             // already ran this slot
        return true;
    }

    /** Epoch millis of the next N scheduled runs (respecting weekend policy). */
    public List<Long> nextRuns(long nowMillis, int count) {
        Config cfg = get();
        List<Long> out = new ArrayList<>();
        if (!cfg.enabled() || count <= 0) return out;
        ZoneId zone = safeZone(cfg.timezone());
        int[] slotMinutes = dailySlotMinutes(cfg);
        LocalDate day = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone).toLocalDate();
        for (int d = 0; d < 400 && out.size() < count; d++, day = day.plusDays(1)) {
            if (cfg.weekdaysOnly() && isWeekend(day)) continue;
            for (int m : slotMinutes) {
                long ts = day.atStartOfDay(zone).plusMinutes(m).toInstant().toEpochMilli();
                if (ts > nowMillis) {
                    out.add(ts);
                    if (out.size() >= count) break;
                }
            }
        }
        return out;
    }

    /** Most recent slot instant at or before now (searching today and yesterday), or -1. */
    private long mostRecentSlot(Config cfg, long nowMillis) {
        ZoneId zone = safeZone(cfg.timezone());
        int[] slotMinutes = dailySlotMinutes(cfg);
        LocalDate today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone).toLocalDate();
        long best = -1;
        for (LocalDate day : new LocalDate[]{today, today.minusDays(1)}) {
            if (cfg.weekdaysOnly() && isWeekend(day)) continue;
            for (int m : slotMinutes) {
                long ts = day.atStartOfDay(zone).plusMinutes(m).toInstant().toEpochMilli();
                if (ts <= nowMillis && ts > best) best = ts;
            }
        }
        return best;
    }

    /**
     * Builds an EventBridge (UTC) cron for a config: {@code cron(min hoursCsv ? * DOW *)}. Slots are
     * anchor + k·interval in the config timezone; converting to UTC yields a constant minute (whole-hour
     * intervals) and a set of UTC hours. Uses the tz offset at "now" — for a DST zone the rule would
     * need re-saving after a transition (IST has no DST, so it's exact there).
     */
    public String buildCron(Config cfg) {
        ZoneId zone = safeZone(cfg.timezone());
        int[] slotMinutes = dailySlotMinutes(cfg);
        LocalDate today = ZonedDateTime.now(zone).toLocalDate();
        java.util.TreeSet<Integer> utcHours = new java.util.TreeSet<>();
        int utcMinute = 0;
        for (int m : slotMinutes) {
            ZonedDateTime utc = today.atStartOfDay(zone).plusMinutes(m).withZoneSameInstant(ZoneId.of("UTC"));
            utcHours.add(utc.getHour());
            utcMinute = utc.getMinute();
        }
        String hours = utcHours.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
        String dow = cfg.weekdaysOnly() ? "MON-FRI" : "*";
        return String.format("cron(%d %s ? * %s *)", utcMinute, hours, dow);
    }

    /** Minutes-of-day for each slot: baseMod, baseMod+cycle, … (interval divides 24h). */
    private static int[] dailySlotMinutes(Config cfg) {
        int cycle = cfg.intervalHours() * 60;
        int base = (cfg.anchorHour() * 60 + cfg.anchorMinute()) % cycle;
        int n = (24 * 60) / cycle;
        int[] slots = new int[n];
        for (int i = 0; i < n; i++) slots[i] = base + i * cycle;
        return slots;
    }

    private long lastRefreshMillis() {
        try {
            String s = meta.get(AppMetaRepository.KEY_LAST_REFRESH_AT);
            return s == null ? 0 : Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static boolean isAllowed(int v) {
        for (int a : ALLOWED_INTERVALS) if (a == v) return true;
        return false;
    }

    private static ZoneId safeZone(String tz) {
        try {
            return ZoneId.of(tz == null || tz.isBlank() ? DEFAULT_TZ : tz);
        } catch (Exception e) {
            return ZoneId.of(DEFAULT_TZ);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }

    private static int parseInt(String s, int dflt) {
        try {
            return s == null ? dflt : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
