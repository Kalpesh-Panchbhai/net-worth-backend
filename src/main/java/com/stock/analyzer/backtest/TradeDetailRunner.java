package com.stock.analyzer.backtest;

import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.model.*;
import com.stock.analyzer.backtest.strategy.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Runs a single strategy on specific stocks and prints per-trade details.
 * Used to verify Pine Script matches Java backtest output.
 */
public class TradeDetailRunner {

    public static void main(String[] args) {
        String[] symbols = {
            "RELIANCE_NS", "HDFCBANK_NS", "TCS_NS", "BHARTIARTL_NS", "ICICIBANK_NS",
            "SBIN_NS", "INFY_NS", "HINDUNILVR_NS", "LICI_NS", "BAJFINANCE_NS"
        };

        // R28:TP0.75 config
        Map<String, Object> params = new HashMap<>();
        params.put("require_bos", false);
        params.put("trend_ema", 0);
        params.put("entry_mode", "rsi");
        params.put("rsi_period", 2);
        params.put("rsi_threshold", 72.0);
        params.put("bos_entry", false);
        params.put("ha_flip_exit", true);
        params.put("ha_exit_bars", 1);
        params.put("sl_atr_mult", 0.0);
        params.put("trailing_stop", true);
        params.put("trail_atr_mult", 0.50);
        params.put("trail_activation_pct", 0.50);
        params.put("atr_period", 20);
        params.put("swing_left", 20);
        params.put("swing_right", 2);
        params.put("bos_exit", true);
        params.put("tp_pct", 0.75);
        params.put("variant_name", "R28:TP0.75");

        DataFetcher fetcher = DataFetcher.createDefault();
        LocalDate tradeFromDt = LocalDate.parse(BacktestConfig.TRADE_FROM);

        for (String symbol : symbols) {
            System.out.printf("%n%s%n", "=".repeat(120));
            System.out.printf("  %s — R28:TP0.75%n", symbol);
            System.out.printf("%s%n", "=".repeat(120));

            List<Bar> bars = fetcher.fetchData(symbol, BacktestConfig.WARMUP_START);
            if (bars == null || bars.size() < 50) {
                System.out.println("  No data or insufficient bars.");
                continue;
            }

            SMCLiteStrategy strategy = new SMCLiteStrategy(params);
            StrategyData data = strategy.computeIndicators(bars);

            TradingViewEngine engine = new TradingViewEngine(
                BacktestConfig.INITIAL_CAPITAL,
                BacktestConfig.COMMISSION_PCT,
                BacktestConfig.SLIPPAGE,
                "percent_of_equity",
                100.0,
                0
            );

            int n = bars.size();
            for (int i = 0; i < n; i++) {
                Bar bar = bars.get(i);
                engine.processBar(bar.getDate(), bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), i);
                boolean tradeActive = !bar.getDate().isBefore(tradeFromDt);
                strategy.onBar(engine, data, i, tradeActive);
            }

            Bar lastBar = bars.get(n - 1);
            engine.finalize(lastBar.getDate(), lastBar.getClose());

            List<ClosedTrade> trades = engine.getClosedTrades();
            Metrics m = engine.getMetrics();

            // Print header
            System.out.printf("  Data: %s to %s (%d bars)  |  Trades from: %s%n",
                bars.get(0).getDate(), lastBar.getDate(), n, BacktestConfig.TRADE_FROM);
            System.out.printf("  Total trades: %d  |  Win: %d  |  Loss: %d  |  WR: %.1f%%  |  PF: %.2f  |  Net: %.2f  |  Return: %.2f%%%n",
                m.getTotalTrades(), m.getWinningTrades(), m.getLosingTrades(),
                m.getWinRate(), m.getProfitFactor(), m.getNetProfit(), m.getTotalReturnPct());
            System.out.printf("  MaxDD: %.2f%%  |  Final Equity: %.2f%n%n", m.getMaxDrawdownPct(), m.getFinalEquity());

            // Print per-trade detail
            System.out.printf("  %-4s %-12s %10s %-12s %10s %10s %8s %5s %-20s %-20s%n",
                "#", "Entry Date", "Entry Px", "Exit Date", "Exit Px", "PnL", "PnL%", "Bars", "Entry Comment", "Exit Comment");
            System.out.printf("  %s%n", "-".repeat(116));

            for (int t = 0; t < trades.size(); t++) {
                ClosedTrade ct = trades.get(t);
                System.out.printf("  %-4d %-12s %10.2f %-12s %10.2f %10.2f %+7.2f%% %5d %-20s %-20s%n",
                    t + 1,
                    ct.getEntryDate(), ct.getEntryPrice(),
                    ct.getExitDate(), ct.getExitPrice(),
                    ct.getPnl(), ct.getPnlPct(), ct.getBarsHeld(),
                    ct.getEntryComment(), ct.getExitComment());
            }

            strategy.reset();
        }
    }
}
