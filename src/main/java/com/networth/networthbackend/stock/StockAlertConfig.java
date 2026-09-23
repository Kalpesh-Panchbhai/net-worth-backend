package com.networth.networthbackend.stock;

import java.time.LocalDate;

/**
 * Per-market Slack alert schedule, editable from the UI (mirrors the old {@code slack_alerts.json}).
 * The alert fires once per day at {@code hour:minute} in {@code timezone}; {@code lastSentDate}
 * guards against duplicate sends within the trigger window.
 */
public record StockAlertConfig(
        String market,
        boolean enabled,
        int hour,
        int minute,
        String timezone,
        boolean weekdaysOnly,
        LocalDate lastSentDate) {
}
