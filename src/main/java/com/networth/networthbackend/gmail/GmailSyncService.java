package com.networth.networthbackend.gmail;

import com.networth.networthbackend.cardspends.CardSpendRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns "the mailbox changed" into balance updates. Lists messages added since the last stored
 * history id, checks each one's headers against {@link BankEmailRules}, and only for a match
 * downloads the body, parses the balance and writes it as the holding's value on that date.
 * <p>
 * The history id is advanced only after every message in the window was handled, so a failure
 * makes Pub/Sub redeliver and the same window is retried. Reprocessing is harmless: the balance is
 * an upsert on (holding, date), so a retry rewrites the same value (and may repeat the Slack post).
 * A card purchase the alert reports is stored once per message id, so a retry does not count it twice.
 * A card payment is added to the latest balance, so it is likewise claimed once per message id first.
 */
@Slf4j
public class GmailSyncService {

    static final String KEY_HISTORY_ID = "gmail_history_id";
    /** app_meta key marking a card payment email as applied, suffixed with its Gmail message id. */
    static final String KEY_PAYMENT_PREFIX = "gmail_payment_";
    static final String ENV_SLACK_WEBHOOK = "GMAIL_SLACK_WEBHOOK_URL";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final GmailClient gmail = new GmailClient();
    private final AppMetaRepository appMeta = new AppMetaRepository();
    private final TransactionRepository transactions = new TransactionRepository();
    private final CardSpendRepository cardSpends = new CardSpendRepository();

    /** Handles one push notification. {@code notifiedHistoryId} is the mailbox's history id at notification time. */
    public synchronized void onNotification(String notifiedHistoryId) throws Exception {
        String start = appMeta.get(KEY_HISTORY_ID);
        if (start == null) {
            // First notification ever: nothing to diff against, so start tracking from here.
            appMeta.set(KEY_HISTORY_ID, notifiedHistoryId);
            log.info("Gmail sync initialised at history id {}", notifiedHistoryId);
            return;
        }
        // Gmail also notifies for changes other than new messages. When the cursor is already
        // at or past the notified id, everything up to it was processed, so skip the Gmail calls.
        if (new BigDecimal(notifiedHistoryId).compareTo(new BigDecimal(start)) <= 0) {
            log.debug("Gmail notification history id {} already covered by {}; skipping", notifiedHistoryId, start);
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
        log.info("Gmail notification history id {}: {} new message(s) since {}",
                notifiedHistoryId, page.addedMessageIds().size(), start);
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
        List<BankEmailRule> rules = BankEmailRules.match(msg);
        if (rules.isEmpty()) return; // not a bank email: never fetch its body
        String names = rules.stream().map(BankEmailRule::name).collect(Collectors.joining(", "));

        if (!msg.dmarcPassed()) {
            log.warn("[{}] message {} from {} failed DMARC; ignoring", names, messageId, msg.fromAddress());
            slack(":rotating_light: " + names + ": email claiming to be from " + msg.fromAddress()
                    + " failed DMARC and was ignored.");
            return;
        }

        // Fetched once and shared by every matching rule.
        GmailMessage full = msg.withBody(gmail.body(messageId));
        if (rules.stream().anyMatch(r -> r.parser().needsPdfs())) full = full.withPdfs(gmail.pdfAttachments(messageId));
        for (BankEmailRule rule : rules) apply(rule, full);
    }

    private void apply(BankEmailRule rule, GmailMessage msg) throws Exception {
        String messageId = msg.id();
        Optional<BankBalanceParser.ParsedBalance> parsed = rule.parser().parse(msg);
        if (parsed.isEmpty()) {
            log.warn("[{}] message {} matched but no balance could be parsed", rule.name(), messageId);
            slack(":warning: " + rule.name() + ": bank email matched but the balance could not be parsed. Subject: " + msg.subject());
            return;
        }

        Optional<AccountResolver.Target> target = rule.account().resolve(msg, parsed.get());
        if (target.isEmpty()) {
            log.warn("[{}] message {} matched but no single account fits subject '{}'", rule.name(), messageId, msg.subject());
            slack(":warning: " + rule.name() + ": no unique account found for \"" + msg.subject() + "\"");
            return;
        }

        BankBalanceParser.ParsedBalance p = parsed.get();
        AccountResolver.Target t = target.get();
        Optional<Transaction> latest = transactions.findLatestByHoldingId(t.holdingId());
        BigDecimal balance = p.balance();
        if (p.payment() != null) {
            // A payment is added to the latest balance, so it must apply once: claim the message first.
            String key = KEY_PAYMENT_PREFIX + messageId;
            if (appMeta.get(key) != null) {
                log.info("[{}] message {} payment already applied; skipping", rule.name(), messageId);
                return;
            }
            appMeta.set(key, p.payment().toPlainString());
            BigDecimal previous = latest.map(Transaction::getValue).orElse(BigDecimal.ZERO);
            balance = previous.add(p.payment());
        }
        if (rule.skipIfUnchanged() && latest.isPresent() && latest.get().getValue() != null
                && latest.get().getValue().compareTo(balance) == 0) {
            log.info("[{}] {} balance unchanged at {}; nothing recorded", rule.name(), t.label(), balance);
            return;
        }
        // Cards record invested = value; a stated invested amount wins; elsewhere a balance changes
        // value, not what was invested.
        BigDecimal invested = t.investedEqualsValue() ? balance
                : p.invested() != null ? p.invested()
                : latest.map(Transaction::getInvested).orElse(balance);
        transactions.upsert(t.accountId(), t.holdingId(), p.asOf(), invested, balance);
        log.info("[{}] {} balance set to {} as of {} from message {}", rule.name(), t.label(), balance, p.asOf(), messageId);
        if (p.spend() != null) {
            cardSpends.insertIgnore(t.accountId(), messageId, p.asOf(), p.spend());
            log.info("[{}] {} spend of {} recorded from message {}", rule.name(), t.label(), p.spend().amount(), messageId);
        }

        // The balance is committed; a refresh failure is picked up by the next scheduled run.
        try {
            DailyDataRunner.runForTransaction(t.accountId(), t.holdingId());
        } catch (Exception e) {
            log.warn("[{}] daily data refresh failed after balance update: {}", rule.name(), e.getMessage(), e);
        }
        if (p.spend() != null) {
            LocalDate monthStart = p.asOf().withDayOfMonth(1);
            BigDecimal monthTotal = cardSpends.sumBetween(t.accountId(), monthStart, p.asOf().withDayOfMonth(p.asOf().lengthOfMonth()));
            slack(":credit_card: " + t.label() + ": " + p.spend().amount().toPlainString() + " spent"
                    + " on " + p.asOf() + "; " + monthTotal.stripTrailingZeros().toPlainString() + " this month; balance " + balance.toPlainString());
        } else if (p.payment() != null) {
            slack(":white_check_mark: " + t.label() + ": payment of " + p.payment().toPlainString() + " received on "
                    + p.asOf() + "; balance " + balance.toPlainString());
        } else {
            slack(":bank: " + t.label() + " balance updated to " + balance.toPlainString() + " (as of " + p.asOf() + ")");
        }
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
