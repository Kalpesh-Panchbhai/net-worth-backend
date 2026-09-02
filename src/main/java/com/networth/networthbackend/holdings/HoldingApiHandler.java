package com.networth.networthbackend.holdings;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.Account;
import com.networth.networthbackend.accounts.AccountRepository;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.SummaryCurrencyConverter;
import com.networth.networthbackend.common.Xirr;
import com.networth.networthbackend.common.XirrService;
import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import com.networth.networthbackend.users.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
public class HoldingApiHandler extends EntityHandlerBase {

    private static final int MONEY_SCALE = 2;

    private final HoldingRepository repository = new HoldingRepository();
    private final AccountRepository accountRepository = new AccountRepository();
    private final UserRepository userRepository = new UserRepository();
    private final SummaryCurrencyConverter currencyConverter = new SummaryCurrencyConverter();
    private final XirrService xirrService = new XirrService();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.DELETE -> delete(request);
            case Constants.Actions.GET -> listByAccountId(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.HOLDINGS, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        String name = json.hasNonNull(Constants.Query.NAME) ? json.get(Constants.Query.NAME).asText() : null;
        String symbol = json.hasNonNull(Constants.Query.SYMBOL) ? json.get(Constants.Query.SYMBOL).asText() : null;
        if (accountId == null || accountId <= 0) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_ACCOUNT_ID_REQUIRED);
        if (name == null || name.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_NAME_REQUIRED);
        if (symbol == null || symbol.isBlank()) return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.HOLDING_SYMBOL_REQUIRED);
        log.debug(Constants.Logs.HOLDING_CREATE, accountId, name, symbol);
        Holding created;
        try {
            created = repository.create(accountId, name, symbol);
        } catch (SQLIntegrityConstraintViolationException e) {
            log.warn(Constants.Logs.HOLDING_SYMBOL_DUPLICATE, accountId, symbol);
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.HOLDING_SYMBOL_DUPLICATE);
        }
        log.info(Constants.Logs.HOLDING_CREATED, created.getId(), created.getAccountId(), created.getName(), created.getSymbol());
        return created(created);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        Long id = parsePathId(request);
        if (id == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.HOLDING_DELETE_AFFECTED, affected, id);
        if (affected == 0) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.HOLDING_NOT_FOUND);
        }
        return ApiGatewayResponses.noContent();
    }

    private APIGatewayProxyResponseEvent listByAccountId(APIGatewayProxyRequestEvent request) throws Exception {
        Long accountId = parsePathId(request);
        if (accountId == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        log.debug(Constants.Logs.HOLDINGS_LIST, accountId);
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.ACCOUNT_NOT_FOUND);
        }
        List<HoldingSummary> list = repository.listSummariesByAccountId(accountId);
        String source = account.getCurrency();
        String preferred = userRepository.getPreferredCurrency(account.getUserId());
        currencyConverter.convert(list, h -> source, preferred);
        boolean unitsAreShares = Constants.Accounts.TYPE_BROKER.equalsIgnoreCase(account.getType());
        for (HoldingSummary h : list) h.setUnitsAreShares(unitsAreShares);
        if (!unitsAreShares) convertUnitsAsMoney(list, source, preferred);
        applyXirr(list, account, preferred);
        log.info(Constants.Logs.HOLDINGS_LIST_COUNT, list.size(), accountId);
        return ok(list);
    }

    /**
     * Converts {@code units} for accounts where that column is a money balance rather than a unit
     * count, at today's rate — the same rate {@code currentDayValue} uses, which for these accounts
     * is the same balance.
     * <p>
     * {@link HoldingSummary} carries no native-currency field, so its only currency label is
     * {@code displayCurrency}; leaving one amount in the account's own currency would leave a
     * client no way to know, and the transactions endpoint already converts this same column.
     * Rows that could not be converted keep their native label, so they are left alone.
     */
    private void convertUnitsAsMoney(List<HoldingSummary> holdings, String sourceCurrency, String preferredCurrency) {
        LocalDate today = LocalDate.now();
        FxRates rates = FxService.rates(sourceCurrency, preferredCurrency, today, today);
        for (HoldingSummary h : holdings) {
            if (!SummaryCurrencyConverter.convertedToPreferred(h, preferredCurrency)) continue;
            BigDecimal converted = rates.convert(h.getUnits(), today);
            if (converted != null) h.setUnits(converted.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
    }

    /**
     * Per-holding XIRR from the account's cash flows, so the holdings list needs no follow-up
     * transaction fetch. Only meaningful where transactions record invested capital.
     */
    private void applyXirr(List<HoldingSummary> holdings, Account account, String preferredCurrency) {
        if (holdings.isEmpty() || !XirrService.isEligible(account.getType(), account.isNeedsDailyData())) return;
        Map<Long, List<Xirr.Flow>> flowsByHolding = xirrService.flowsByHolding(
                account.getId(), account.getCurrency(), preferredCurrency);
        if (flowsByHolding.isEmpty()) return;
        for (HoldingSummary h : holdings) {
            if (!SummaryCurrencyConverter.convertedToPreferred(h, preferredCurrency)) continue;
            h.setXirr(xirrService.solve(flowsByHolding.get(h.getId()), h.getCurrentDayValue()));
        }
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
