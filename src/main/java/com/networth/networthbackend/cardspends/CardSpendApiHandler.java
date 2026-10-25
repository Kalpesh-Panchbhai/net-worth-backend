package com.networth.networthbackend.cardspends;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
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
 */
public class CardSpendApiHandler extends EntityHandlerBase {

    static final String CONFIG_KEY = "card_budget";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CardSpendRepository repository = new CardSpendRepository();
    private final UserConfigRepository userConfig = new UserConfigRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        if (!Constants.Actions.GET.equalsIgnoreCase(action)) {
            return badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "card-spends", action));
        }
        Long userId = parsePathId(request);
        if (userId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        LocalDate today = LocalDate.now(IST);
        YearMonth month;
        String monthParam = queryParam(request, "month");
        try {
            month = monthParam == null || monthParam.isBlank() ? YearMonth.from(today) : YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException e) {
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
