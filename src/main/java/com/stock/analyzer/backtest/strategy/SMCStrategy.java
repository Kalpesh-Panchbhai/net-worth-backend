package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.indicator.Indicators;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Smart Money Concept (SMC) Strategy — Timeframe: 1D (Daily)
 *
 * Core concepts:
 * 1. Swing Structure: Detect swing highs/lows with configurable confirmation delay
 * 2. Break of Structure (BOS): Trend determination via swing level breaks
 * 3. Order Blocks (OB): Last opposing candle before BOS impulse
 * 4. Fair Value Gaps (FVG): 3-candle imbalance zones
 * 5. Optional EMA trend filter
 *
 * Entry types (long only): 'bos', 'ob', 'fvg', 'ob_fvg'
 * Exit: Bearish BOS or SL/TP hit.
 */
public class SMCStrategy implements Strategy {

    private String name = "SMC BOS";
    private final String timeframe = "1D";
    private String description = "Break of Structure (15/5) — no TP, exit on bearish BOS";

    // Parameters
    private final int swingLeft;
    private final int swingRight;
    private final String entryType;
    private final double slAtrMult;
    private final double tpRr;
    private final int emaPeriod;
    private final int obMaxAge;
    private final int fvgMaxAge;
    private final int atrPeriod;
    private final boolean trailingSl;
    private final boolean bosReentry;

    // State
    private int[] lastSh;  // {barIdx, (unused)} + price stored separately
    private double lastShPrice;
    private int[] lastSl;
    private double lastSlPrice;
    private int structure;         // 1=bullish, -1=bearish
    private int[] structLow;
    private double structLowPrice;
    private List<double[]> activeObs;  // {bar, high, low}
    private List<double[]> activeFvgs; // {bar, high, low, type(1=bull,-1=bear)}
    private int bosBbar;
    private boolean stoppedOut;

    public SMCStrategy(int swingLeft, int swingRight, String entryType,
                       double slAtrMult, double tpRr, int emaPeriod,
                       int obMaxAge, int fvgMaxAge, int atrPeriod,
                       boolean trailingSl, boolean bosReentry,
                       String variantName) {
        this.swingLeft = swingLeft;
        this.swingRight = swingRight;
        this.entryType = entryType;
        this.slAtrMult = slAtrMult;
        this.tpRr = tpRr;
        this.emaPeriod = emaPeriod;
        this.obMaxAge = obMaxAge;
        this.fvgMaxAge = fvgMaxAge;
        this.atrPeriod = atrPeriod;
        this.trailingSl = trailingSl;
        this.bosReentry = bosReentry;

        if (variantName != null) {
            this.name = variantName;
            this.description = variantName;
        }
        resetState();
    }

    // Default constructor
    public SMCStrategy() {
        this(15, 5, "bos", 1.5, 0, 0, 30, 20, 14, false, false, null);
    }

    public SMCStrategy(Map<String, Object> params) {
        this(
            getInt(params, "swing_left", 15),
            getInt(params, "swing_right", 5),
            getString(params, "entry_type", "bos"),
            getDouble(params, "sl_atr_mult", 1.5),
            getDouble(params, "tp_rr", 0),
            getInt(params, "ema_period", 0),
            getInt(params, "ob_max_age", 30),
            getInt(params, "fvg_max_age", 20),
            getInt(params, "atr_period", 14),
            getBool(params, "trailing_sl", false),
            getBool(params, "bos_reentry", false),
            getString(params, "variant_name", null)
        );
    }

    private void resetState() {
        lastSh = null; lastShPrice = 0;
        lastSl = null; lastSlPrice = 0;
        structure = 0;
        structLow = null; structLowPrice = 0;
        activeObs = new ArrayList<>();
        activeFvgs = new ArrayList<>();
        bosBbar = -1;
        stoppedOut = false;
    }

    @Override public String getName() { return name; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public String getDescription() { return description; }
    @Override public void reset() { resetState(); }

    @Override
    public StrategyData computeIndicators(List<Bar> bars) {
        StrategyData data = new StrategyData(bars);
        double[] atr = Indicators.atr(data.high(), data.low(), data.close(), atrPeriod);
        data.put("ATR", atr);

        if (emaPeriod > 0) {
            data.put("EMA", Indicators.emaAdjustFalse(data.close(), emaPeriod));
        }
        return data;
    }

    @Override
    public void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive) {
        int minBars = swingLeft + swingRight + 1;
        if (i < minBars) return;

        double[] high = data.high();
        double[] low = data.low();
        double[] close = data.close();
        double[] open = data.open();
        double atrVal = data.get("ATR")[i];

        if (Double.isNaN(atrVal) || atrVal <= 0) return;

        // 1. Detect confirmed swing points (no lookahead)
        int pivot = i - swingRight;
        boolean isSh = detectSwingHigh(high, pivot);
        if (isSh) {
            lastSh = new int[]{pivot};
            lastShPrice = high[pivot];
        }

        boolean isSlPt = detectSwingLow(low, pivot);
        if (isSlPt) {
            lastSl = new int[]{pivot};
            lastSlPrice = low[pivot];
        }

        // 2. Detect FVG
        if (i >= 2) {
            if (low[i] > high[i - 2]) {
                activeFvgs.add(new double[]{i, low[i], high[i - 2], 1}); // bull
            }
            if (high[i] < low[i - 2]) {
                activeFvgs.add(new double[]{i, low[i - 2], high[i], -1}); // bear
            }
        }

        // Expire old zones
        activeFvgs.removeIf(f -> i - (int) f[0] > fvgMaxAge);
        activeObs.removeIf(ob -> i - (int) ob[0] > obMaxAge);

        // 3. Break of Structure detection
        // Bullish BOS
        if (lastSh != null && close[i] > lastShPrice && structure != 1) {
            structure = 1;
            bosBbar = i;
            if (lastSl != null) {
                structLow = new int[]{lastSl[0]};
                structLowPrice = lastSlPrice;
            }
            // Create Order Block
            int searchFrom = Math.max(0, (lastSl != null ? lastSl[0] : i - 20));
            for (int k = i - 1; k >= searchFrom; k--) {
                if (close[k] < open[k]) {
                    activeObs.add(new double[]{k, open[k], low[k]});
                    break;
                }
            }
        }

        // Bearish BOS
        if (lastSl != null && close[i] < lastSlPrice && structure != -1) {
            structure = -1;
            bosBbar = i;
            stoppedOut = false;
            if (engine.getPosition() != null) {
                engine.closeAll("Bearish BOS");
            }
            activeObs.clear();
        }

        // 3b. Trailing SL
        if (trailingSl && engine.getPosition() != null
                && engine.getPosition().getStopLoss() != null
                && isSlPt && lastSl != null) {
            double newSl = lastSlPrice - slAtrMult * atrVal;
            if (newSl > engine.getPosition().getStopLoss()) {
                engine.getPosition().setStopLoss(newSl);
            }
        }

        // Track stopouts
        if (!stoppedOut && structure == 1 && engine.getPosition() == null && bosBbar < i) {
            var trades = engine.getClosedTrades();
            if (!trades.isEmpty()) {
                String exitComment = trades.get(trades.size() - 1).getExitComment();
                if ("Stop Loss".equals(exitComment) || "Take Profit".equals(exitComment)) {
                    stoppedOut = true;
                }
            }
        }

        if (!tradeActive) return;

        // 4. Entry logic (long only)
        if (structure != 1 || engine.getPosition() != null) return;

        // EMA trend filter
        if (emaPeriod > 0) {
            double ema = data.get("EMA")[i];
            if (!Double.isNaN(ema) && close[i] < ema) return;
        }

        if ("bos".equals(entryType)) {
            if (bosBbar == i) {
                double sl = getBosSl(close[i], atrVal);
                if (sl >= close[i]) return;
                Double tp = getTp(close[i], sl);
                engine.entry("Long", Direction.LONG, sl, tp, "BOS Entry");
                stoppedOut = false;
            } else if (bosReentry && stoppedOut) {
                if (tryObEntry(engine, close[i], low[i], atrVal)) {
                    stoppedOut = false;
                }
            }
        } else if ("ob".equals(entryType)) {
            tryObEntry(engine, close[i], low[i], atrVal);
        } else if ("fvg".equals(entryType)) {
            tryFvgEntry(engine, close[i], low[i], atrVal);
        } else if ("ob_fvg".equals(entryType)) {
            tryObFvgEntry(engine, close[i], low[i], atrVal);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean detectSwingHigh(double[] high, int pivot) {
        for (int j = 1; j <= swingLeft; j++) {
            if (pivot - j < 0 || high[pivot] <= high[pivot - j]) return false;
        }
        for (int j = 1; j <= swingRight; j++) {
            if (high[pivot] <= high[pivot + j]) return false;
        }
        return true;
    }

    private boolean detectSwingLow(double[] low, int pivot) {
        for (int j = 1; j <= swingLeft; j++) {
            if (pivot - j < 0 || low[pivot] >= low[pivot - j]) return false;
        }
        for (int j = 1; j <= swingRight; j++) {
            if (low[pivot] >= low[pivot + j]) return false;
        }
        return true;
    }

    private double getBosSl(double price, double atrVal) {
        if (structLow != null) {
            return structLowPrice - slAtrMult * atrVal;
        }
        return price - 3.0 * atrVal;
    }

    private Double getTp(double entryPrice, double slPrice) {
        if (tpRr <= 0) return null;
        double risk = entryPrice - slPrice;
        if (risk <= 0) return null;
        return entryPrice + risk * tpRr;
    }

    private boolean tryObEntry(TradingViewEngine engine, double closePrice, double lowPrice, double atrVal) {
        Iterator<double[]> it = activeObs.iterator();
        while (it.hasNext()) {
            double[] ob = it.next();
            if (lowPrice <= ob[1] && closePrice > ob[2]) {
                double sl = ob[2] - slAtrMult * atrVal;
                if (sl >= closePrice) continue;
                Double tp = getTp(closePrice, sl);
                engine.entry("Long", Direction.LONG, sl, tp, "OB Entry");
                it.remove();
                return true;
            }
        }
        return false;
    }

    private boolean tryFvgEntry(TradingViewEngine engine, double closePrice, double lowPrice, double atrVal) {
        Iterator<double[]> it = activeFvgs.iterator();
        while (it.hasNext()) {
            double[] fvg = it.next();
            if (fvg[3] != 1) continue; // bull only
            if (lowPrice <= fvg[1] && closePrice > fvg[2]) {
                double sl = fvg[2] - slAtrMult * atrVal;
                if (sl >= closePrice) continue;
                Double tp = getTp(closePrice, sl);
                engine.entry("Long", Direction.LONG, sl, tp, "FVG Entry");
                it.remove();
                return true;
            }
        }
        return false;
    }

    private boolean tryObFvgEntry(TradingViewEngine engine, double closePrice, double lowPrice, double atrVal) {
        Iterator<double[]> obIt = activeObs.iterator();
        while (obIt.hasNext()) {
            double[] ob = obIt.next();
            if (lowPrice <= ob[1] && closePrice > ob[2]) {
                boolean hasFvg = false;
                for (double[] fvg : activeFvgs) {
                    if (fvg[3] == 1 && fvg[2] <= ob[1] && fvg[1] >= ob[2]) {
                        hasFvg = true;
                        break;
                    }
                }
                if (hasFvg) {
                    double sl = ob[2] - slAtrMult * atrVal;
                    if (sl >= closePrice) continue;
                    Double tp = getTp(closePrice, sl);
                    engine.entry("Long", Direction.LONG, sl, tp, "OB+FVG Entry");
                    obIt.remove();
                    return true;
                }
            }
        }
        return false;
    }

    // ── Static param helpers ─────────────────────────────────────
    static int getInt(Map<String, Object> m, String k, int def) {
        Object v = m.get(k); return v != null ? ((Number) v).intValue() : def;
    }
    static double getDouble(Map<String, Object> m, String k, double def) {
        Object v = m.get(k); return v != null ? ((Number) v).doubleValue() : def;
    }
    static boolean getBool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k); return v != null ? (Boolean) v : def;
    }
    static String getString(Map<String, Object> m, String k, String def) {
        Object v = m.get(k); return v != null ? v.toString() : def;
    }
}
