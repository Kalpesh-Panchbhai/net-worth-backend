package com.networth.networthbackend.chartdata;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.accounts.Account;
import com.networth.networthbackend.accounts.AccountRepository;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.users.UserRepository;
import com.networth.networthbackend.watchlists.Watchlist;
import com.networth.networthbackend.watchlists.WatchlistRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ChartDataApiHandler extends EntityHandlerBase {

    private final ChartDataService service = new ChartDataService();
    private final TimelineService timelineService = new TimelineService();
    private final ChartDataValidationService validationService = new ChartDataValidationService();
    private final AccountRepository accountRepository = new AccountRepository();
    private final WatchlistRepository watchlistRepository = new WatchlistRepository();
    private final UserRepository userRepository = new UserRepository();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }

        return switch (action.toLowerCase()) {
            case Constants.Actions.GET -> getChartData(request);
            case "timeline" -> getTimeline(request);
            default ->
                    badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "chartdata", action));
        };
    }

    /** GET /chart-data?action=timeline&watchlistId=&step= — per-holding history for the time machine. */
    private APIGatewayProxyResponseEvent getTimeline(APIGatewayProxyRequestEvent request) throws Exception {
        long watchlistId;
        int step = TimelineService.DEFAULT_STEP_DAYS;
        try {
            watchlistId = Long.parseLong(queryParam(request, "watchlistId"));
            String stepParam = queryParam(request, "step");
            if (stepParam != null) step = Integer.parseInt(stepParam);
        } catch (NumberFormatException e) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "watchlistId and step must be integers");
        }
        if (step < 1 || step > 366) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "step must be between 1 and 366 days");
        }
        Watchlist watchlist = watchlistRepository.findById(watchlistId).orElse(null);
        if (watchlist == null) {
            return notFound(Constants.ErrorCodes.VALIDATION_ERROR, "No watchlist with id " + watchlistId);
        }
        String preferred = userRepository.getPreferredCurrency(watchlist.getUserId());
        return ok(timelineService.getWatchlistTimeline(watchlistId, step, preferred));
    }

    private APIGatewayProxyResponseEvent getChartData(APIGatewayProxyRequestEvent request) throws Exception {
        // Parse request parameters
        String entityTypeParam = queryParam(request, "entityType");
        String entityIdParam = queryParam(request, "entityId");
        String timePeriod = queryParam(request, "timePeriod");

        // Basic parameter validation (no entity existence check)
        ChartDataValidationService.ValidationResult validationResult = validationService.validateChartDataParameters(entityTypeParam, entityIdParam, timePeriod);

        if (!validationResult.valid()) {
            log.warn("Chart data request validation failed: {}", validationService.getValidationSummary(validationResult));
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, validationResult.getFirstError());
        }

        // Extract validated parameters
        EntityType entityType = EntityType.fromCode(entityTypeParam).get(); // Safe since validation passed
        long entityId = Long.parseLong(entityIdParam); // Safe since validation passed

        // Get chart data using generic method, then convert to the owner's preferred display currency.
        List<ChartData> chartData;
        try {
            log.debug("Fetching chart data for {}: entityId={}, timePeriod={}", entityType.getCode(), entityId, timePeriod);

            chartData = fetchConvertedChartData(entityType, entityId, timePeriod);

            log.info("Successfully fetched {} chart data points for {} with ID {}", chartData.size(), entityType.getCode(), entityId);

            return ok(chartData);

        } catch (Exception e) {
            log.error("Error fetching chart data for {} with ID {}: {}", entityType.getCode(), entityId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Every branch returns amounts in the owner's display currency, or an empty series. An unknown
     * entity id yields nothing rather than falling through to the raw SQL, whose watchlist variant
     * sums member accounts without any FX conversion.
     */
    private List<ChartData> fetchConvertedChartData(EntityType entityType, long entityId, String timePeriod) throws Exception {
        return switch (entityType) {
            case HOLDING -> convertSingleAccountSeries(
                    accountRepository.findByHoldingId(entityId).orElse(null), entityType, entityId, timePeriod);
            case ACCOUNT -> convertSingleAccountSeries(
                    accountRepository.findById(entityId).orElse(null), entityType, entityId, timePeriod);
            case WATCHLIST -> {
                Watchlist watchlist = watchlistRepository.findById(entityId).orElse(null);
                if (watchlist == null) {
                    log.warn("No watchlist with id {}; returning an empty chart series", entityId);
                    yield List.of();
                }
                yield service.getWatchlistChartDataConverted(
                        entityId, timePeriod, userRepository.getPreferredCurrency(watchlist.getUserId()));
            }
        };
    }

    private List<ChartData> convertSingleAccountSeries(Account account, EntityType entityType, long entityId, String timePeriod) throws Exception {
        if (account == null) {
            log.warn("No account behind {} id {}; returning an empty chart series", entityType.getCode(), entityId);
            return List.of();
        }
        String preferred = userRepository.getPreferredCurrency(account.getUserId());
        List<ChartData> data = service.getChartData(entityType, entityId, timePeriod);
        service.convertSeries(data, account.getCurrency(), preferred);
        service.applyAccountSeriesXirr(data, account, entityType, entityId, preferred);
        return data;
    }
}
