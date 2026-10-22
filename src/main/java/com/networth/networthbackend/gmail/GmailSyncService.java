package com.networth.networthbackend.gmail;

import com.networth.networthbackend.meta.AppMetaRepository;
import com.networth.networthbackend.tools.DailyDataRunner;
import com.networth.networthbackend.transactions.Transaction;
import com.networth.networthbackend.transactions.TransactionRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Turns "the mailbox changed" into balance updates. Lists messages added since the last stored
 * history id, checks each one's headers against {@link BankEmailRules}, and only for a match
 * downloads the body, parses the balance and writes it as the holding's value on that date.
 * <p>
 * The history id is advanced only after every message in the window was handled, so a failure
 * makes Pub/Sub redeliver and the same window is retried. Reprocessing is harmless: the balance is
 * an upsert on (holding, date), so a retry rewrites the same value (and may repeat the Slack post).
 */
@Slf4j
public class GmailSyncService {

    static final String KEY_HISTORY_ID = "gmail_history_id";
    static final String ENV_SLACK_WEBHOOK = "GMAIL_SLACK_WEBHOOK_URL";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final GmailClient gmail = new GmailClient();
    private final AppMetaRepository appMeta = new AppMetaRepository();
    private final TransactionRepository transactions = new TransactionRepository();

    /** Handles one push notification. {@code notifiedHistoryId} is the mailbox's history id at notification time. */
    public synchronized void onNotification(String notifiedHistoryId) throws Exception {
        String start = appMeta.get(KEY_HISTORY_ID);
        if (start == null) {
            // First notification ever: nothing to diff against, so start tracking from here.
            appMeta.set(KEY_HISTORY_ID, notifiedHistoryId);
            log.info("Gmail sync initialised at history id {}", notifiedHistoryId);
            return;
        }
        GmailClient.HistoryPage page;
        try {
            page = gmail.addedSince(start);
        } catch (GmailClient.HistoryExpiredException e) {
            log.warn("Stored Gmail history id {} expired; resetting to {}. Emails in the gap are not processed.", start, notifiedHistoryId);
            slack(":warning: Gmail sync history expired; bank emails received while it was down were skipped.");
            appMeta.set(KEY_HISTORY_ID, notifiedHistoryId);
            return;
        }
        for (String id : page.addedMessageIds()) {
            handle(id);
        }
        appMeta.set(KEY_HISTORY_ID, maxId(page.historyId(), notifiedHistoryId));
    }

    /** Renews the Gmail watch (it lapses after 7 days) and seeds the history id if none is stored. */
    public void renewWatch() throws Exception {
        String historyId = gmail.watch();
        if (appMeta.get(KEY_HISTORY_ID) == null) appMeta.set(KEY_HISTORY_ID, historyId);
    }

    private void handle(String messageId) throws Exception {
        GmailMessage msg = gmail.headers(messageId);
        if (msg == null) return; // deleted before we got to it
        BankEmailRule rule = BankEmailRules.match(msg);
        if (rule == null) return; // not a bank email: never fetch its body

        if (!msg.dmarcPassed()) {
            log.warn("[{}] message {} from {} failed DMARC; ignoring", rule.name(), messageId, msg.fromAddress());
            slack(":rotating_light: " + rule.name() + ": email claiming to be from " + msg.fromAddress()
                    + " failed DMARC and was ignored.");
            return;
        }

        Optional<BankBalanceParser.ParsedBalance> parsed = rule.parser().parse(msg.withBody(gmail.body(messageId)));
        if (parsed.isEmpty()) {
            log.warn("[{}] message {} matched but no balance could be parsed", rule.name(), messageId);
            slack(":warning: " + rule.name() + ": bank email matched but the balance could not be parsed. Subject: " + msg.subject());
            return;
        }

        BankBalanceParser.ParsedBalance p = parsed.get();
        // A balance changes the holding's value, not what was invested, so carry invested forward.
        BigDecimal invested = transactions.findLatestByHoldingId(rule.holdingId())
                .map(Transaction::getInvested).orElse(p.balance());
        transactions.upsert(rule.accountId(), rule.holdingId(), p.asOf(), invested, p.balance());
        log.info("[{}] balance set to {} as of {} from message {}", rule.name(), p.balance(), p.asOf(), messageId);

        // The balance is committed; a refresh failure is picked up by the next scheduled run.
        try {
            DailyDataRunner.runForTransaction(rule.accountId(), rule.holdingId());
        } catch (Exception e) {
            log.warn("[{}] daily data refresh failed after balance update: {}", rule.name(), e.getMessage(), e);
        }
        slack(":bank: " + rule.name() + " balance updated to " + p.balance().toPlainString() + " (as of " + p.asOf() + ")");
    }

    /** Gmail history ids are increasing integers sent as strings; never move the cursor backwards. */
    private static String maxId(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return new BigDecimal(a).compareTo(new BigDecimal(b)) >= 0 ? a : b;
    }

    /** Best-effort Slack post; a no-op when the webhook is not configured. */
    private static void slack(String text) {
        String webhook = System.getenv(ENV_SLACK_WEBHOOK);
        if (webhook == null || webhook.isBlank()) return;
        try {
            String body = com.networth.networthbackend.common.Jsons.MAPPER.writeValueAsString(Map.of("text", text));
            HTTP.send(HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Gmail sync Slack post failed: {}", e.getMessage());
        }
    }
}
