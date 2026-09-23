package com.stock.analyzer.backtest.ranking;

import com.stock.analyzer.backtest.model.ClosedTrade;
import com.stock.analyzer.backtest.model.EquityPoint;
import com.stock.analyzer.backtest.model.Metrics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link Metrics} object for a sub-window of an existing backtest
 * result. Allows scoring a single continuous backtest at four different
 * windows (IS_FULL, IS_EARLY, OOS_HIST, OOS_RECENT) without re-running it.
 *
 * <p>Window restriction rules:
 *   • Equity curve: points with {@code from ≤ date ≤ to} (or to=null for open-ended).
 *   • Trades: included if their <b>exit</b> date falls inside the window —
 *     PnL is realised on exit, so that is when the trade "counts."
 *   • Final equity, MDD, CAGR derived from the sliced equity curve.
 *   • Sharpe computed from daily returns within the window.
 *
 * <p>The initial-equity baseline is the equity value at the first in-window
 * bar (not the original initial capital), so windowed returns are correctly
 * computed relative to "where the strategy was at window start."
 */
public final class SubWindowMetrics {

    private SubWindowMetrics() {}

    /**
     * Compute Metrics for the sub-window {@code [from, to]} (inclusive on both
     * ends; pass {@code to == null} to mean "open-ended to end of curve").
     */
    public static Metrics slice(List<EquityPoint> equityCurve,
                                List<ClosedTrade> allTrades,
                                LocalDate from,
                                LocalDate to,
                                double originalInitialCapital) {
        if (equityCurve == null || equityCurve.size() < 2) return emptyMetrics(originalInitialCapital);

        // ── Sliced equity curve ──────────────────────────────────────
        List<EquityPoint> sliced = new ArrayList<>();
        for (EquityPoint p : equityCurve) {
            LocalDate d = p.getDate();
            if (d.isBefore(from)) continue;
            if (to != null && d.isAfter(to)) break;
            sliced.add(p);
        }
        if (sliced.size() < 2) return emptyMetrics(originalInitialCapital);

        double startEquity = sliced.get(0).getEquity();
        double endEquity   = sliced.get(sliced.size() - 1).getEquity();
        if (startEquity <= 0) return emptyMetrics(originalInitialCapital);

        // Total return % relative to window-start equity.
        double totalReturnPct = (endEquity - startEquity) / startEquity * 100.0;

        // MDD computed inside the window (peak-to-trough on in-window equity).
        double mdd = 0.0;
        double peak = startEquity;
        for (EquityPoint p : sliced) {
            if (p.getEquity() > peak) peak = p.getEquity();
            double dd = (p.getEquity() - peak) / peak * 100.0;
            if (dd < mdd) mdd = dd;
        }

        // Daily returns within window → Sharpe.
        double sumR = 0, sumR2 = 0;
        int nR = 0;
        for (int i = 1; i < sliced.size(); i++) {
            double prev = sliced.get(i - 1).getEquity();
            double cur  = sliced.get(i).getEquity();
            if (prev <= 0) continue;
            double r = cur / prev - 1.0;
            sumR += r;
            sumR2 += r * r;
            nR++;
        }
        double sharpe = 0;
        if (nR >= 2) {
            double meanR = sumR / nR;
            double varR  = (sumR2 - nR * meanR * meanR) / (nR - 1);
            double std   = Math.sqrt(Math.max(varR, 0));
            if (std > 0) sharpe = (meanR / std) * Math.sqrt(252);
        }

        // ── Sliced trades (closed inside window) ─────────────────────
        int totalTrades = 0, winners = 0, losers = 0, breakevens = 0;
        double grossProfit = 0, grossLoss = 0;
        double sumWinPct = 0, sumLossPct = 0;
        double maxWin = Double.NEGATIVE_INFINITY, maxLoss = Double.POSITIVE_INFINITY;
        long sumBars = 0;
        double totalCommission = 0;

        for (ClosedTrade t : allTrades) {
            LocalDate xd = t.getExitDate();
            if (xd == null) continue;
            if (xd.isBefore(from)) continue;
            if (to != null && xd.isAfter(to)) continue;

            totalTrades++;
            sumBars += t.getBarsHeld();
            totalCommission += t.getCommission();
            if (t.getPnl() > 0) {
                winners++;
                grossProfit += t.getPnl();
                sumWinPct += t.getPnlPct();
            } else if (t.getPnl() < 0) {
                losers++;
                grossLoss += t.getPnl();
                sumLossPct += t.getPnlPct();
            } else {
                breakevens++;
            }
            maxWin = Math.max(maxWin, t.getPnl());
            maxLoss = Math.min(maxLoss, t.getPnl());
        }

        double winRate = totalTrades > 0 ? (double) winners / totalTrades * 100.0 : 0.0;
        double pf;
        if (grossLoss != 0) pf = Math.min(grossProfit / Math.abs(grossLoss), 999.99);
        else pf = totalTrades > 0 ? 999.99 : 0;
        double netProfit = grossProfit + grossLoss;

        return Metrics.builder()
            .netProfit(round2(netProfit))
            .netProfitPct(round2(totalReturnPct))
            .grossProfit(round2(grossProfit))
            .grossLoss(round2(grossLoss))
            .totalTrades(totalTrades)
            .winningTrades(winners)
            .losingTrades(losers)
            .breakevenTrades(breakevens)
            .winRate(round2(winRate))
            .profitFactor(round4(pf))
            .avgTrade(totalTrades > 0 ? round2(netProfit / totalTrades) : 0)
            .avgWin(winners > 0 ? round2(grossProfit / winners) : 0)
            .avgLoss(losers > 0 ? round2(grossLoss / losers) : 0)
            .avgWinPct(winners > 0 ? round2(sumWinPct / winners) : 0)
            .avgLossPct(losers > 0 ? round2(sumLossPct / losers) : 0)
            .avgBarsHeld(totalTrades > 0 ? round1((double) sumBars / totalTrades) : 0)
            .maxWin(round2(maxWin == Double.NEGATIVE_INFINITY ? 0 : maxWin))
            .maxLoss(round2(maxLoss == Double.POSITIVE_INFINITY ? 0 : maxLoss))
            .maxDrawdownPct(round2(mdd))
            .maxConsecutiveWins(0)
            .maxConsecutiveLosses(0)
            .totalCommission(round2(totalCommission))
            .finalEquity(round2(endEquity))
            .initialCapital(round2(startEquity))
            .totalReturnPct(round2(totalReturnPct))
            .sharpeRatio(round4(sharpe))
            .build();
    }

    private static Metrics emptyMetrics(double initialCapital) {
        return Metrics.builder()
            .initialCapital(initialCapital)
            .finalEquity(initialCapital)
            .build();
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
