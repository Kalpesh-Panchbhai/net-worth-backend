package com.networth.networthbackend.refresh;

import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.RuleState;

/**
 * Thin wrapper over the AWS EventBridge rule that triggers the scheduler Lambda. Lets the UI read
 * the live cron/state and update it. The rule name and region come from env (with sensible
 * defaults); credentials are the Lambda execution role.
 */
@Slf4j
public class EventBridgeScheduler {

    /** Name of the EventBridge rule wired to NetWorthSchedulerFunction. */
    public static final String RULE_NAME =
            System.getenv().getOrDefault("SCHEDULER_RULE_NAME", "DailyDataCreatorScheduler");

    /** Per-market Slack-alert rules (fire NetWorthStockAlertFunction at each market's configured time). */
    public static String alertRule(String market) {
        return "StockAlertScheduler-" + ("us".equalsIgnoreCase(market) ? "US" : "IN");
    }

    private static volatile EventBridgeClient client;

    private static EventBridgeClient client() {
        if (client == null) {
            synchronized (EventBridgeScheduler.class) {
                if (client == null) {
                    String region = System.getenv().getOrDefault("AWS_REGION", "ap-south-1");
                    client = EventBridgeClient.builder()
                            .region(Region.of(region))
                            .httpClient(UrlConnectionHttpClient.create())
                            .build();
                }
            }
        }
        return client;
    }

    public record RuleInfo(String scheduleExpression, String state) {
    }

    /** Builds a once-a-day UTC cron for a local {@code hour:minute} in {@code tz} (optionally weekdays). */
    public static String dailyCron(int hour, int minute, String tz, boolean weekdaysOnly) {
        ZoneId zone;
        try {
            zone = ZoneId.of(tz == null || tz.isBlank() ? "Asia/Kolkata" : tz);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
        }
        ZonedDateTime utc = java.time.LocalDate.now(zone).atTime(hour, minute).atZone(zone)
                .withZoneSameInstant(ZoneId.of("UTC"));
        String dow = weekdaysOnly ? "MON-FRI" : "*";
        return String.format("cron(%d %d ? * %s *)", utc.getMinute(), utc.getHour(), dow);
    }

    /** Reads a rule's current schedule expression and state (ENABLED/DISABLED). */
    public RuleInfo describe(String ruleName) {
        DescribeRuleResponse r = client().describeRule(DescribeRuleRequest.builder().name(ruleName).build());
        return new RuleInfo(r.scheduleExpression(), r.stateAsString());
    }

    /** Updates a rule's cron and enabled state, preserving its existing targets. */
    public void update(String ruleName, String cronExpression, boolean enabled) {
        client().putRule(PutRuleRequest.builder()
                .name(ruleName)
                .scheduleExpression(cronExpression)
                .state(enabled ? RuleState.ENABLED : RuleState.DISABLED)
                .build());
        log.info("Updated EventBridge rule {} -> {} ({})", ruleName, cronExpression, enabled ? "ENABLED" : "DISABLED");
    }
}
