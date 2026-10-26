package com.networth.networthbackend.cardspends;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networth.networthbackend.accounts.Account;
import com.networth.networthbackend.accounts.AccountRepository;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.gmail.Last4Resolver;
import com.networth.networthbackend.userconfig.UserConfigRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * {@code GET /card-spends/{userId}?month=2026-10} — credit card spending for budgeting. {@code spent} sums
 * the purchases read from alert emails in that calendar month (bill payments are never counted);
 * {@code debt} is what is owed right now, from each card account's latest balance. Accounts are per bank,
 * since a bank's cards share one limit. The budget is the user's {@code card_budget} config
 * ({@code {"monthlyLimit": 40000}}); without one, the budget fields are null. {@code month} defaults to the
 * current month (IST).
 *
 * <p>Individual spends: {@code GET /card-spends/{userId}?action=list&month=&accountId=} lists them,
 * {@code POST /card-spends} adds one by hand ({@code accountId, amount, spentOn, cardLast4}),
 * {@code PATCH /card-spends/{id}} edits one (an email row is then flagged {@code edited}) and
 * {@code DELETE /card-spends/{id}} removes one. Debt is untouched by these, since it comes from the
 * card's balance, not from the spend rows.
 */
public class CardSpendApiHandler extends EntityHandlerBase {

    static final String CONFIG_KEY = "card_budget";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CardSpendRepository repository = new CardSpendRepository();
    private final CardSpendEntryRepository entries = new CardSpendEntryRepository();
    private final AccountRepository accountRepository = new AccountRepository();
    private final UserConfigRepository userConfig = new UserConfigRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> summary(request);
            case "list" -> list(request);
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.UPDATE -> update(request);
            case Constants.Actions.DELETE -> delete(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "card-spends", action));
        };
    }

    private APIGatewayProxyResponseEvent summary(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        LocalDate today = LocalDate.now(IST);
        YearMonth month = parseMonth(request, today);
        if (month == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'month' must be YYYY-MM");
        }

        List<CardSpendRepository.CardTotal> cards = repository.totalsForUser(userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        ObjectNode out = MAPPER.createObjectNode();
        ArrayNode byAccount = MAPPER.createArrayNode();
        for (CardSpendRepository.CardTotal c : cards) {
            // Balances are stored negated (money owed); debt is shown positive.
            BigDecimal owed = c.balance().negate().max(BigDecimal.ZERO);
            spent = spent.add(c.spent());
            debt = debt.add(owed);
            byAccount.addObject()
                    .put("accountId", c.accountId())
                    .put("name", c.name())
                    .put("spent", c.spent())
                    .put("debt", owed);
        }
        out.put("month", month.toString());
        out.put("spent", spent);
        out.put("debt", debt);

        Optional<BigDecimal> budget = budget(userId);
        if (budget.isPresent()) {
            BigDecimal left = budget.get().subtract(spent);
            out.put("budget", budget.get());
            out.put("left", left);
            // Days still to spend in, today included; only meaningful for the current month.
            if (month.equals(YearMonth.from(today))) {
                int daysLeft = month.lengthOfMonth() - today.getDayOfMonth() + 1;
                out.put("perDayLeft", left.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP));
            } else {
                out.putNull("perDayLeft");
            }
        } else {
            out.putNull("budget");
            out.putNull("left");
            out.putNull("perDayLeft");
        }
        out.set("byAccount", byAccount);
        return ok(out);
    }

    private APIGatewayProxyResponseEvent list(APIGatewayProxyRequestEvent request) throws Exception {
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        YearMonth month = parseMonth(request, LocalDate.now(IST));
        if (month == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'month' must be YYYY-MM");
        }
        Long accountId;
        String accountParam = queryParam(request, Constants.Query.ACCOUNT_ID);
        try {
            accountId = accountParam == null || accountParam.isBlank() ? null : Long.parseLong(accountParam.trim());
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'accountId' must be a number");
        }
        ArrayNode out = MAPPER.createArrayNode();
        for (CardSpendEntryRepository.CardSpend s : entries.listForUser(userId, accountId, month.atDay(1), month.atEndOfMonth())) {
            out.add(toJson(s));
        }
        return ok(out);
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        SpendInput in = parseInput(json);
        if (in.error() != null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, in.error());
        long id = entries.create(in.accountId(), in.amount(), in.cardLast4(), in.spentOn());
        return created(toJson(entries.findById(id).orElseThrow()));
    }

    private APIGatewayProxyResponseEvent update(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        JsonNode json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Optional<CardSpendEntryRepository.CardSpend> existing = entries.findById(id);
        if (existing.isEmpty()) return notFound(Constants.ErrorCodes.NOT_FOUND, "No card spend with id " + id);
        SpendInput in = parseInput(json);
        if (in.error() != null) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, in.error());
        // Moving a spend to another user's card would leak it across accounts.
        Optional<Account> from = accountRepository.findById(existing.get().accountId());
        if (from.isPresent() && from.get().getUserId() != in.account().getUserId()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "accountId must be one of your credit card accounts");
        }
        entries.update(id, in.accountId(), in.amount(), in.cardLast4(), in.spentOn());
        return ok(toJson(entries.findById(id).orElseThrow()));
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        if (entries.deleteById(id) == 0) return notFound(Constants.ErrorCodes.NOT_FOUND, "No card spend with id " + id);
        return ok(MAPPER.createObjectNode().put("id", id));
    }

    /** A validated create/update body; {@code error} is set instead when it is not usable. */
    private record SpendInput(Account account, BigDecimal amount, String cardLast4, LocalDate spentOn, String error) {
        long accountId() {
            return account.getId();
        }

        static SpendInput invalid(String error) {
            return new SpendInput(null, null, null, null, error);
        }
    }

    private SpendInput parseInput(JsonNode json) throws Exception {
        JsonNode accountNode = json.get(Constants.Query.ACCOUNT_ID);
        if (accountNode == null || !accountNode.canConvertToLong()) return SpendInput.invalid("accountId is required");
        Account account = accountRepository.findById(accountNode.asLong()).orElse(null);
        if (account == null || !Last4Resolver.TYPE_CREDIT_CARD.equals(account.getType())) {
            return SpendInput.invalid("accountId must be one of your credit card accounts");
        }
        JsonNode amountNode = json.get("amount");
        if (amountNode == null || !amountNode.isNumber() || amountNode.decimalValue().signum() <= 0) {
            return SpendInput.invalid("amount must be a positive number");
        }
        LocalDate spentOn;
        try {
            spentOn = LocalDate.parse(json.path("spentOn").asText(""));
        } catch (DateTimeParseException e) {
            return SpendInput.invalid("spentOn must be a date (YYYY-MM-DD)");
        }
        String last4 = json.hasNonNull("cardLast4") ? json.get("cardLast4").asText().trim() : "";
        if (!last4.isEmpty() && !last4.matches("\\d{4}")) return SpendInput.invalid("cardLast4 must be 4 digits");
        return new SpendInput(account, amountNode.decimalValue(), last4.isEmpty() ? null : last4, spentOn, null);
    }

    private ObjectNode toJson(CardSpendEntryRepository.CardSpend s) {
        return MAPPER.createObjectNode()
                .put("id", s.id())
                .put("accountId", s.accountId())
                .put("accountName", s.accountName())
                .put("amount", s.amount())
                .put("cardLast4", s.cardLast4())
                .put("spentOn", s.spentOn().toString())
                .put("source", s.fromEmail() ? "email" : "manual")
                .put("edited", s.edited());
    }

    private YearMonth parseMonth(APIGatewayProxyRequestEvent request, LocalDate today) {
        String monthParam = queryParam(request, "month");
        try {
            return monthParam == null || monthParam.isBlank() ? YearMonth.from(today) : YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Optional<BigDecimal> budget(long userId) throws Exception {
        Optional<String> stored = userConfig.find(userId, CONFIG_KEY);
        if (stored.isEmpty()) return Optional.empty();
        JsonNode limit = MAPPER.readTree(stored.get()).get("monthlyLimit");
        return limit != null && limit.isNumber() ? Optional.of(limit.decimalValue()) : Optional.empty();
    }

    private Long parsePathId(APIGatewayProxyRequestEvent request) {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) return null;
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
