package com.stock.analyzer.backtest.engine;

import com.stock.analyzer.backtest.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Replicates TradingView's Pine Script strategy tester behavior.
 *
 * Key behaviors:
 * 1. Next-bar-open execution: Signal on bar close -> order fills at NEXT bar's
 * open
 * 2. SL/TP detected at bar close (high/low compared to levels) -> queues exit
 * ->
 * fills at NEXT bar's open (matches manual workflow: scanner runs 5 PM IST,
 * user executes at next session's open). NO intrabar fill.
 * 3. Position sizing: percent_of_equity or fixed quantity
 * 4. Commission: Applied on both entry and exit (% of trade value)
 * 5. Slippage: Fixed amount added to buys, subtracted from sells
 * 6. Pyramiding: Default 0 (no stacking)
 * 7. Equity = cash + open position market value (mark-to-market)
 * 8. Same bar SL+TP: SL takes priority (pessimistic)
 */
public class TradingViewEngine {

    private final double initialCapital;
    private final double commissionPct;
    private final double slippage;
    private final String defaultQtyType;
    private final double defaultQtyValue;
    private final int pyramiding;

    private double cash;
    private Position position;
    private final List<PendingOrder> pendingOrders = new ArrayList<>();
    private final List<ClosedTrade> closedTrades = new ArrayList<>();
    private final List<EquityPoint> equityCurve = new ArrayList<>();

    private double peakEquity;
    private int currentBarIndex;
    private LocalDate currentDate;
    private double lastClose;
    private int positionCount;

    public TradingViewEngine(double initialCapital, double commissionPct, double slippage,
            String defaultQtyType, double defaultQtyValue, int pyramiding) {
        this.initialCapital = initialCapital;
        this.commissionPct = commissionPct;
        this.slippage = slippage;
        this.defaultQtyType = defaultQtyType;
        this.defaultQtyValue = defaultQtyValue;
        this.pyramiding = pyramiding;

        this.cash = initialCapital;
        this.peakEquity = initialCapital;
        this.position = null;
        this.positionCount = 0;
    }

    public TradingViewEngine(double initialCapital, double commissionPct, double slippage) {
        this(initialCapital, commissionPct, slippage, "percent_of_equity", 100.0, 0);
    }

    public TradingViewEngine(double initialCapital) {
        this(initialCapital, 0.0, 0.0);
    }

    // ── Properties ────────────────────────────────────────────────

    public double getEquity() {
        if (position != null) {
            return cash + position.getQty() * lastClose;
        }
        return cash;
    }

    public Position getPosition() {
        return position;
    }

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public List<EquityPoint> getEquityCurve() {
        return equityCurve;
    }

    public int getCurrentBarIndex() {
        return currentBarIndex;
    }

    /** Returns true if an exit order is queued (will fill at next bar's open). */
    public boolean hasPendingExit() {
        for (PendingOrder o : pendingOrders) {
            String id = o.getId();
            if ("__close_position__".equals(id) || "__close_all__".equals(id))
                return true;
        }
        return false;
    }

    /** Comment of first queued exit, or null. */
    public String getPendingExitComment() {
        for (PendingOrder o : pendingOrders) {
            String id = o.getId();
            if ("__close_position__".equals(id) || "__close_all__".equals(id))
                return o.getComment();
        }
        return null;
    }

    /** Returns true if a market-entry order is queued (LONG or SHORT). */
    public boolean hasPendingEntry() {
        for (PendingOrder o : pendingOrders) {
            String id = o.getId();
            if ("__close_position__".equals(id) || "__close_all__".equals(id))
                continue;
            if (o.getOrderType() == OrderType.MARKET)
                return true;
        }
        return false;
    }

    // ── Slippage / Commission ─────────────────────────────────────

    private double applySlippage(double price, boolean isBuy) {
        return isBuy ? price + slippage : price - slippage;
    }

    private double calcCommission(double price, double qty) {
        return Math.abs(price * qty) * (commissionPct / 100.0);
    }

    private double calcQty(double fillPrice, PendingOrder order) {
        if (order.getQty() != null)
            return order.getQty();

        double pct;
        if (order.getQtyPercent() != null) {
            pct = order.getQtyPercent() / 100.0;
        } else if ("percent_of_equity".equals(defaultQtyType)) {
            pct = defaultQtyValue / 100.0;
        } else {
            return defaultQtyValue; // fixed qty
        }

        double equity = position == null ? getEquity() : cash + (position.getQty() * fillPrice);
        double usable = equity * pct;
        double commPerUnit = fillPrice * (commissionPct / 100.0);
        double effectivePrice = fillPrice + commPerUnit;
        if (effectivePrice <= 0)
            return 0;
        return Math.max(0, usable / effectivePrice);
    }

    // ── Fill logic ────────────────────────────────────────────────

    private void fillEntry(PendingOrder order, double fillPrice, LocalDate fillDate, int barIndex) {
        boolean isBuy = order.getDirection() == Direction.LONG;
        fillPrice = applySlippage(fillPrice, isBuy);
        double qty = calcQty(fillPrice, order);
        if (qty <= 0)
            return;

        double commission = calcCommission(fillPrice, qty);
        cash -= (fillPrice * qty) + commission;

        position = new Position(
                order.getDirection(), fillPrice, qty, fillDate, barIndex,
                order.getStopLoss(), order.getTakeProfit(), order.getComment());
        positionCount++;
    }

    private void fillExit(double fillPrice, LocalDate fillDate, int barIndex, String comment) {
        if (position == null)
            return;

        boolean isSell = position.getDirection() == Direction.LONG;
        fillPrice = applySlippage(fillPrice, !isSell);

        Position pos = position;
        double commissionExit = calcCommission(fillPrice, pos.getQty());

        double pnl;
        if (pos.getDirection() == Direction.LONG) {
            pnl = (fillPrice - pos.getEntryPrice()) * pos.getQty() - commissionExit;
        } else {
            pnl = (pos.getEntryPrice() - fillPrice) * pos.getQty() - commissionExit;
        }

        double pnlPct = ((fillPrice / pos.getEntryPrice()) - 1) * 100;
        if (pos.getDirection() == Direction.SHORT)
            pnlPct = -pnlPct;

        cash += fillPrice * pos.getQty() - commissionExit;

        int barsHeld = barIndex - pos.getEntryBar();

        // MAE/MFE: factor in the exit fill itself in case it was the worst/best point.
        pos.trackAdverse(fillPrice, fillPrice);
        pos.trackFavorable(fillPrice, fillPrice);
        double maePct = pos.maePct();
        double mfePct = pos.mfePct();

        closedTrades.add(ClosedTrade.builder()
                .direction(pos.getDirection().getValue())
                .entryDate(pos.getEntryDate())
                .entryPrice(pos.getEntryPrice())
                .exitDate(fillDate)
                .exitPrice(fillPrice)
                .qty(pos.getQty())
                .pnl(pnl)
                .pnlPct(pnlPct)
                .maePct(maePct)
                .mfePct(mfePct)
                .barsHeld(barsHeld)
                .entryComment(pos.getComment())
                .exitComment(comment != null ? comment : "")
                .commission(commissionExit)
                .build());

        position = null;
        positionCount = 0;
    }

    // ── SL/TP check ───────────────────────────────────────────────
    // Signals at bar close → queue exit → fills at NEXT bar's open.
    // Aligns with manual workflow: scanner runs at 5 PM IST after market close,
    // user can only act next trading day's open.

    private void checkSlTp(double high, double low) {
        if (position == null)
            return;

        Double sl = position.getStopLoss();
        Double tp = position.getTakeProfit();
        if (sl == null && tp == null)
            return;

        boolean slHit = false, tpHit = false;
        if (position.getDirection() == Direction.LONG) {
            if (sl != null && low <= sl)
                slHit = true;
            if (tp != null && high >= tp)
                tpHit = true;
        } else { // SHORT
            if (sl != null && high >= sl)
                slHit = true;
            if (tp != null && low <= tp)
                tpHit = true;
        }

        if (!slHit && !tpHit)
            return;
        // SL takes priority when both trigger
        String comment = slHit ? "Stop Loss" : "Take Profit";

        // Avoid duplicate queue if strategy.onBar already queued a close on same bar
        for (PendingOrder existing : pendingOrders) {
            String id = existing.getId();
            if ("__close_position__".equals(id) || "__close_all__".equals(id))
                return;
        }

        pendingOrders.add(PendingOrder.builder()
                .id("__close_position__")
                .direction(Direction.LONG)
                .comment(comment)
                .build());
    }

    // ── Process bar ───────────────────────────────────────────────

    public void processBar(LocalDate date, double open, double high, double low, double close, int barIndex) {
        this.currentBarIndex = barIndex;
        this.currentDate = date;
        this.lastClose = close;

        // 1. Fill pending market orders at this bar's open
        List<PendingOrder> ordersToFill = new ArrayList<>(pendingOrders);
        pendingOrders.clear();

        for (PendingOrder order : ordersToFill) {
            if (order.getOrderType() == OrderType.MARKET) {
                String id = order.getId();
                if ("__close_all__".equals(id) || "__close_position__".equals(id)) {
                    if (position != null) {
                        fillExit(open, date, barIndex, order.getComment());
                    }
                } else if (order.getDirection() == Direction.LONG || order.getDirection() == Direction.SHORT) {
                    // If opposite position, close first
                    if (position != null && position.getDirection() != order.getDirection()) {
                        fillExit(open, date, barIndex, "Reverse");
                    }
                    if (position == null || positionCount < pyramiding) {
                        fillEntry(order, open, date, barIndex);
                    }
                }
            }
        }

        // 2. Track MAE/MFE on the open position before SL/TP can close it (so the
        // adverse/favorable extreme on this bar is considered even on the exit bar).
        if (position != null) {
            position.trackAdverse(high, low);
            position.trackFavorable(high, low);
        }

        // 3. Check SL/TP at bar close — queues exit for next bar open (no intrabar
        // fill)
        checkSlTp(high, low);

        // 4. Record equity
        double eq = cash + (position != null ? position.getQty() * close : 0);
        peakEquity = Math.max(peakEquity, eq);
        double ddPct = peakEquity > 0 ? ((eq - peakEquity) / peakEquity) * 100 : 0;
        equityCurve.add(new EquityPoint(date, eq, ddPct));
    }

    // ── Entry / Exit commands ─────────────────────────────────────

    public void entry(String id, Direction direction, Double stopLoss, Double takeProfit,
            Double qty, Double qtyPercent, String comment, boolean when) {
        if (!when)
            return;

        // Don't add duplicate entries if already positioned
        if (position != null && position.getDirection() == direction && positionCount > pyramiding) {
            return;
        }

        pendingOrders.add(PendingOrder.builder()
                .id(id)
                .direction(direction)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .qty(qty)
                .qtyPercent(qtyPercent)
                .comment(comment != null ? comment : "")
                .barIndex(currentBarIndex)
                .build());
    }

    public void entry(String id, String direction, Double stopLoss, Double takeProfit, String comment) {
        entry(id, Direction.fromString(direction), stopLoss, takeProfit, null, null, comment, true);
    }

    public void entry(String id, Direction direction, Double stopLoss, Double takeProfit, String comment) {
        entry(id, direction, stopLoss, takeProfit, null, null, comment, true);
    }

    public void closeAll(String comment) {
        pendingOrders.add(PendingOrder.builder()
                .id("__close_all__")
                .direction(Direction.LONG)
                .comment(comment != null ? comment : "Close")
                .build());
    }

    public void close(String comment) {
        pendingOrders.add(PendingOrder.builder()
                .id("__close_position__")
                .direction(Direction.LONG)
                .comment(comment != null ? comment : "Close")
                .build());
    }

    public void finalize(LocalDate lastDate, double lastClose) {
        if (position != null) {
            LocalDate date = lastDate != null ? lastDate : currentDate;
            fillExit(lastClose, date, currentBarIndex, "End of Data");
        }
    }

    // ── Metrics ───────────────────────────────────────────────────

    public Metrics getMetrics() {
        if (closedTrades.isEmpty())
            return emptyMetrics();

        int totalTrades = closedTrades.size();
        List<ClosedTrade> winning = new ArrayList<>();
        List<ClosedTrade> losing = new ArrayList<>();
        int breakeven = 0;

        double grossProfit = 0, grossLoss = 0, totalCommission = 0;
        double maxWin = Double.NEGATIVE_INFINITY, maxLoss = Double.POSITIVE_INFINITY;
        double sumWinPct = 0, sumLossPct = 0;
        long sumBars = 0;

        for (ClosedTrade t : closedTrades) {
            totalCommission += t.getCommission();
            sumBars += t.getBarsHeld();
            if (t.getPnl() > 0) {
                winning.add(t);
                grossProfit += t.getPnl();
                sumWinPct += t.getPnlPct();
            } else if (t.getPnl() < 0) {
                losing.add(t);
                grossLoss += t.getPnl();
                sumLossPct += t.getPnlPct();
            } else {
                breakeven++;
            }
            maxWin = Math.max(maxWin, t.getPnl());
            maxLoss = Math.min(maxLoss, t.getPnl());
        }

        double netProfit = grossProfit + grossLoss;
        double winRate = totalTrades > 0 ? (double) winning.size() / totalTrades * 100 : 0;
        double profitFactor = grossLoss != 0 ? Math.min(grossProfit / Math.abs(grossLoss), 999.99) : 999.99;

        double avgTrade = totalTrades > 0 ? netProfit / totalTrades : 0;
        double avgWin = !winning.isEmpty() ? grossProfit / winning.size() : 0;
        double avgLoss = !losing.isEmpty() ? grossLoss / losing.size() : 0;
        double avgWinPct = !winning.isEmpty() ? sumWinPct / winning.size() : 0;
        double avgLossPct = !losing.isEmpty() ? sumLossPct / losing.size() : 0;
        double avgBars = totalTrades > 0 ? (double) sumBars / totalTrades : 0;

        double maxDdPct = equityCurve.stream().mapToDouble(EquityPoint::getDrawdownPct).min().orElse(0);
        double finalEquity = equityCurve.isEmpty() ? initialCapital
                : equityCurve.get(equityCurve.size() - 1).getEquity();
        double totalReturnPct = ((finalEquity - initialCapital) / initialCapital) * 100;

        int[] consec = maxConsecutive();
        double sharpe = calcSharpe();

        return Metrics.builder()
                .netProfit(round2(netProfit))
                .netProfitPct(round2(totalReturnPct))
                .grossProfit(round2(grossProfit))
                .grossLoss(round2(grossLoss))
                .totalTrades(totalTrades)
                .winningTrades(winning.size())
                .losingTrades(losing.size())
                .breakevenTrades(breakeven)
                .winRate(round2(winRate))
                .profitFactor(round4(profitFactor))
                .avgTrade(round2(avgTrade))
                .avgWin(round2(avgWin))
                .avgLoss(round2(avgLoss))
                .avgWinPct(round2(avgWinPct))
                .avgLossPct(round2(avgLossPct))
                .avgBarsHeld(round1(avgBars))
                .maxWin(round2(maxWin == Double.NEGATIVE_INFINITY ? 0 : maxWin))
                .maxLoss(round2(maxLoss == Double.POSITIVE_INFINITY ? 0 : maxLoss))
                .maxDrawdownPct(round2(maxDdPct))
                .maxConsecutiveWins(consec[0])
                .maxConsecutiveLosses(consec[1])
                .totalCommission(round2(totalCommission))
                .finalEquity(round2(finalEquity))
                .initialCapital(initialCapital)
                .totalReturnPct(round2(totalReturnPct))
                .sharpeRatio(round4(sharpe))
                .build();
    }

    private int[] maxConsecutive() {
        int maxW = 0, maxL = 0, curW = 0, curL = 0;
        for (ClosedTrade t : closedTrades) {
            if (t.getPnl() > 0) {
                curW++;
                curL = 0;
            } else if (t.getPnl() < 0) {
                curL++;
                curW = 0;
            } else {
                curW = 0;
                curL = 0;
            }
            maxW = Math.max(maxW, curW);
            maxL = Math.max(maxL, curL);
        }
        return new int[] { maxW, maxL };
    }

    private double calcSharpe() {
        if (equityCurve.size() < 2)
            return 0.0;
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = equityCurve.get(i - 1).getEquity();
            double cur = equityCurve.get(i).getEquity();
            if (prev > 0)
                returns.add(cur / prev - 1);
        }
        if (returns.isEmpty())
            return 0.0;

        double sum = 0;
        for (double r : returns)
            sum += r;
        double mean = sum / returns.size();

        double sumSq = 0;
        for (double r : returns)
            sumSq += (r - mean) * (r - mean);
        double std = returns.size() > 1 ? Math.sqrt(sumSq / (returns.size() - 1)) : 0.0001;

        if (std == 0)
            return 0.0;
        return (mean / std) * Math.sqrt(252);
    }

    private Metrics emptyMetrics() {
        return Metrics.builder()
                .initialCapital(initialCapital)
                .finalEquity(initialCapital)
                .build();
    }

    private static double round1(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v))
            return v;
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v))
            return v;
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v))
            return v;
        return Math.round(v * 10000.0) / 10000.0;
    }
}
