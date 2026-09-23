package com.networth.networthbackend.stock;

import com.stock.analyzer.backtest.config.Market;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Steady-state path for the stock analyzer: for each market, scan every tracked stock with its
 * sector's winning strategy, persist the resulting signals, and fire the Slack alert. Runs inside
 * the scheduled daily job (out of the 29-second manual-refresh path), because a full multi-market
 * Yahoo scan takes a few minutes.
 */
@Slf4j
public class StockSignalRefreshService {

    /** Markets scanned each run. India first (its close is earlier), then US. */
    private static final String[] MARKETS = {"in", "us"};

    private final StockSignalRepository signalRepository;
    private final StockPositionRepository positionRepository;
    private final SlackNotifier slack;

    public StockSignalRefreshService() {
        this(new StockSignalRepository(), new StockPositionRepository(), new SlackNotifier());
    }

    public StockSignalRefreshService(StockSignalRepository signalRepository,
                                     StockPositionRepository positionRepository,
                                     SlackNotifier slack) {
        this.signalRepository = signalRepository;
        this.positionRepository = positionRepository;
        this.slack = slack;
    }

    public record Result(int scanned, int written) {
        public static Result empty() {
            return new Result(0, 0);
        }
    }

    /** Scans and stores every market, then alerts. Never throws: a bad market must not fail the job. */
    public Result refresh() {
        int scanned = 0;
        int written = 0;
        for (String market : MARKETS) {
            try {
                Result r = refreshMarket(market);
                scanned += r.scanned();
                written += r.written();
            } catch (Exception e) {
                log.error("[{}] stock signal refresh failed; keeping previous snapshot: {}", market, e.getMessage(), e);
            }
        }
        return new Result(scanned, written);
    }

    private Result refreshMarket(String market) throws Exception {
        List<StockSignal> signals = new StockSignalScanner(Market.of(market), 12).scan();
        if (signals.isEmpty()) {
            log.warn("[{}] scan produced no signals (data fetch likely failed); keeping previous snapshot", market);
            return Result.empty();
        }
        // FLAT = no position and no signal: the uninteresting majority. Store only actionable states.
        List<StockSignal> actionable = signals.stream()
                .filter(s -> !"FLAT".equals(s.type()))
                .toList();
        int written = signalRepository.replaceForMarket(market, actionable);
        log.info("[{}] stock signal refresh: {} scanned, {} actionable, {} written",
                market, signals.size(), actionable.size(), written);
        // Slack alerts are dispatched separately by StockAlertService at the user-configured time,
        // reading these freshly-stored signals — so the scan no longer sends them itself.
        return new Result(signals.size(), written);
    }
}
