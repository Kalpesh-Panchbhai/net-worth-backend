package com.networth.networthbackend.refresh;

import lombok.extern.slf4j.Slf4j;
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

    /** Reads the rule's current schedule expression and state (ENABLED/DISABLED). */
    public RuleInfo describe() {
        DescribeRuleResponse r = client().describeRule(DescribeRuleRequest.builder().name(RULE_NAME).build());
        return new RuleInfo(r.scheduleExpression(), r.stateAsString());
    }

    /** Updates the rule's cron and enabled state, preserving its existing targets. */
    public void update(String cronExpression, boolean enabled) {
        client().putRule(PutRuleRequest.builder()
                .name(RULE_NAME)
                .scheduleExpression(cronExpression)
                .state(enabled ? RuleState.ENABLED : RuleState.DISABLED)
                .build());
        log.info("Updated EventBridge rule {} -> {} ({})", RULE_NAME, cronExpression, enabled ? "ENABLED" : "DISABLED");
    }
}
