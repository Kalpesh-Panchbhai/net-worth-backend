package com.networth.networthbackend.transactions;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.Account;
import com.networth.networthbackend.accounts.AccountRepository;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.tools.DailyDataRunner;
import com.networth.networthbackend.users.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;

@Slf4j
public class TransactionApiHandler extends EntityHandlerBase {

    private final TransactionRepository repository = new TransactionRepository();
    private final AccountRepository accountRepository = new AccountRepository();
    private final UserRepository userRepository = new UserRepository();
    private final TransactionCurrencyConverter currencyConverter = new TransactionCurrencyConverter();

    /**
     * True when an account's transaction {@code value} column holds units rather than money.
     * Broker accounts store unit counts; a non-broker account flagged {@code needsDailyData} is
     * fed from a broker API that reports a balance, so its {@code value} is money.
     */
    private static boolean valueIsUnits(Account account) {
        return Constants.Accounts.TYPE_BROKER.equalsIgnoreCase(account.getType());
    }

    private void convert(List<Transaction> txns, Account account) throws java.sql.SQLException {
        if (txns.isEmpty()) return;
        if (account == null) {
            // Should not happen (the rows were fetched via the account), but the contract says
            // displayCurrency is always set, so label the rows rather than emitting nulls.
            log.warn("No account for {} transaction(s); labelling them with the default currency", txns.size());
            for (Transaction t : txns) currencyConverter.labelNative(t, null, false);
            return;
        }
        currencyConverter.convert(txns, account.getCurrency(), userRepository.getPreferredCurrency(account.getUserId()), valueIsUnits(account));
    }

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }
        return switch (action.toLowerCase()) {
            case Constants.Actions.CREATE -> create(request);
            case Constants.Actions.GET -> list(request);
            case Constants.Actions.DELETE -> delete(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, Constants.Entities.TRANSACTIONS, action));
        };
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) throws Exception {
        var json = getJsonBody(request);
        if (json == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.REQUEST_BODY_REQUIRED);
        }
        Long accountId = json.hasNonNull(Constants.Query.ACCOUNT_ID) ? json.get(Constants.Query.ACCOUNT_ID).asLong() : null;
        Long holdingId = json.hasNonNull(Constants.Query.HOLDING_ID) ? json.get(Constants.Query.HOLDING_ID).asLong() : null;
        String txnDateStr = json.hasNonNull(Constants.Query.TXN_DATE) ? json.get(Constants.Query.TXN_DATE).asText() : null;
        BigDecimal invested = json.hasNonNull(Constants.Query.INVESTED) ? json.get(Constants.Query.INVESTED).decimalValue() : null;
        BigDecimal value = json.hasNonNull(Constants.Query.VALUE) ? json.get(Constants.Query.VALUE).decimalValue() : null;
        String mode = json.hasNonNull(Constants.Query.MODE) ? json.get(Constants.Query.MODE).asText() : null;

        if (accountId == null || accountId <= 0)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_ACCOUNT_ID_REQUIRED);
        if (holdingId == null || holdingId < 0)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_HOLDING_ID_REQUIRED);
        if (txnDateStr == null || txnDateStr.isBlank())
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_DATE_REQUIRED);
        if (invested == null)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_INVESTED_REQUIRED);
        if (value == null)
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_VALUE_REQUIRED);
        if (mode == null || mode.isBlank())
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_MODE_REQUIRED);
        String m = mode.toLowerCase();
        if (!m.equals("add") && !m.equals("update"))
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_MODE_INVALID);

        LocalDate txnDate;
        try {
            txnDate = LocalDate.parse(txnDateStr);
        } catch (Exception e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_DATE_REQUIRED);
        }
        log.debug(Constants.Logs.TRANSACTION_CREATE, accountId, holdingId, txnDate, invested, value, m);
        Transaction created;
        try {
            BigDecimal effInvested = invested;
            BigDecimal effValue = value;
            if (m.equals("add")) {
                var latestOpt = repository.findLatestByHoldingId(holdingId);
                if (latestOpt.isPresent()) {
                    var latest = latestOpt.get();
                    effInvested = effInvested.add(latest.getInvested());
                    effValue = effValue.add(latest.getValue());
                }
            }
            created = repository.create(accountId, holdingId, txnDate, effInvested, effValue);
        } catch (SQLIntegrityConstraintViolationException e) {
            return ApiGatewayResponses.error(Constants.Http.Status.CONFLICT, Constants.ErrorCodes.CONFLICT, Constants.Messages.TRANSACTION_DUPLICATE);
        }
        // The echoed row is exactly what was stored, i.e. the account's native currency. Label it
        // as such so a client cannot mistake it for a display-currency figure and write it back.
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null) {
            currencyConverter.labelNative(created, account.getCurrency(), valueIsUnits(account));
        }
        // The transaction is already committed, so a refresh failure must not fail the request;
        // the scheduled run will pick it up later.
        try {
            DailyDataRunner.runForTransaction(accountId, holdingId);
        } catch (Exception e) {
            log.warn("Daily data refresh failed for account {} holding {} after transaction create: {}", accountId, holdingId, e.getMessage(), e);
        }
        log.info(Constants.Logs.TRANSACTION_CREATED, created.getId(), accountId, holdingId, txnDate, invested, value, m);
        return created(created);
    }

    private APIGatewayProxyResponseEvent list(APIGatewayProxyRequestEvent request) throws Exception {
        String accountIdStr = queryParam(request, Constants.Query.ACCOUNT_ID);
        String holdingIdStr = queryParam(request, Constants.Query.HOLDING_ID);
        if ((accountIdStr == null || accountIdStr.isBlank()) && (holdingIdStr == null || holdingIdStr.isBlank())) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.TRANSACTION_FILTER_REQUIRED);
        }
        if (holdingIdStr != null && !holdingIdStr.isBlank()) {
            long holdingId = Long.parseLong(holdingIdStr);
            log.debug(Constants.Logs.TRANSACTIONS_LIST, Constants.Query.HOLDING_ID, holdingId);
            var list = repository.listByHoldingId(holdingId);
            convert(list, accountRepository.findByHoldingId(holdingId).orElse(null));
            log.info(Constants.Logs.TRANSACTIONS_LIST_COUNT, list.size(), Constants.Query.HOLDING_ID, holdingId);
            return ok(list);
        }
        long accountId = Long.parseLong(accountIdStr);
        log.debug(Constants.Logs.TRANSACTIONS_LIST, Constants.Query.ACCOUNT_ID, accountId);
        var list = repository.listByAccountId(accountId);
        convert(list, accountRepository.findById(accountId).orElse(null));
        log.info(Constants.Logs.TRANSACTIONS_LIST_COUNT, list.size(), Constants.Query.ACCOUNT_ID, accountId);
        return ok(list);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) throws Exception {
        String idStr = pathParam(request, Constants.Path.ID);
        if (idStr == null || idStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, Constants.Messages.PATH_ID_REQUIRED);
        }
        long id = Long.parseLong(idStr);
        // Read the row first: once it is gone there is no way to tell which account/holding to refresh.
        var existing = repository.findById(id);
        int affected = repository.deleteById(id);
        log.debug(Constants.Logs.TRANSACTION_DELETE_AFFECTED, affected, id);
        if (affected == 0) return notFound(Constants.ErrorCodes.NOT_FOUND, Constants.Messages.TRANSACTION_NOT_FOUND);
        // The delete is already committed, so a refresh failure must not fail the request;
        // the scheduled run will pick it up later.
        if (existing.isPresent()) {
            long accountId = existing.get().getAccountId();
            long holdingId = existing.get().getHoldingId();
            try {
                DailyDataRunner.runForTransaction(accountId, holdingId);
            } catch (Exception e) {
                log.warn("Daily data refresh failed for account {} holding {} after transaction delete: {}", accountId, holdingId, e.getMessage(), e);
            }
        }
        return ApiGatewayResponses.noContent();
    }
}
