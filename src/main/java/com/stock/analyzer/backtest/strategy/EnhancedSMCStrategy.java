package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.indicator.Indicators;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.stock.analyzer.backtest.strategy.SMCStrategy.*;

/**
 * Enhanced Smart Money Concept (SMC) Strategy — Timeframe: 1D (Daily)
 *
 * Built on top of SMC BOS SL3, adding:
 * 1. ADX filter — avoid sideways markets
 * 2. Volume confirmation
 * 3. MACD confirmation
 * 4. ATR-based trailing stop
 * 5. EMA trend filter
 * 6. BOS re-entry via OB pullback after stopout
 * 7. Take profit via R:R ratio
 * 8. Swing parameter tuning
 */
public class EnhancedSMCStrategy implements Strategy {

    private String name = "Enhanced SMC";
    private final String timeframe = "1D";
    private String description = "Enhanced SMC BOS with filters and trailing";

    // Swing structure params
    private final int swingLeft;
    private final int swingRight;
    private final int atrPeriod;
    // SL / TP
    private final double slAtrMult;
    private final double tpRr;
    // ADX filter
    private final boolean adxFilter;
    private final int adxPeriod;
    private final double adxThreshold;
    // Volume filter
    private final boolean volumeFilter;
    private final int volSmaPeriod;
    private final double volMult;
    // MACD filter
    private final boolean macdFilter;
    private final int macdFast;
    private final int macdSlow;
    private final int macdSignal;
    // EMA filter
    private final boolean emaFilter;
    private final int emaPeriod;
    // Trailing stop
    private final boolean trailingStop;
    private final double trailAtrMult;
    private final boolean trailSwing;
    // Re-entry
    private final boolean bosReentry;
    // OB/FVG
    private final int obMaxAge;
    private final int fvgMaxAge;

    // State
    private int[] lastSh; private double lastShPrice;
    private int[] lastSl; private double lastSlPrice;
    private int structure;
    private int[] structLow; private double structLowPrice;
    private List<double[]> activeObs;
    private List<double[]> activeFvgs;
    private int bosBbar;
    private boolean stoppedOut;
    private double trailHigh;
    private int entryBar;

    public EnhancedSMCStrategy(int swingLeft, int swingRight, int atrPeriod,
                                double slAtrMult, double tpRr,
                                boolean adxFilter, int adxPeriod, double adxThreshold,
                                boolean volumeFilter, int volSmaPeriod, double volMult,
                                boolean macdFilter, int macdFast, int macdSlow, int macdSignal,
                                boolean emaFilter, int emaPeriod,
                                boolean trailingStop, double trailAtrMult, boolean trailSwing,
                                boolean bosReentry,
                                int obMaxAge, int fvgMaxAge,
                                String variantName) {
        this.swingLeft = swingLeft;
        this.swingRight = swingRight;
        this.atrPeriod = atrPeriod;
        this.slAtrMult = slAtrMult;
        this.tpRr = tpRr;
        this.adxFilter = adxFilter;
        this.adxPeriod = adxPeriod;
        this.adxThreshold = adxThreshold;
        this.volumeFilter = volumeFilter;
        this.volSmaPeriod = volSmaPeriod;
        this.volMult = volMult;
        this.macdFilter = macdFilter;
        this.macdFast = macdFast;
        this.macdSlow = macdSlow;
        this.macdSignal = macdSignal;
        this.emaFilter = emaFilter;
        this.emaPeriod = emaPeriod;
        this.trailingStop = trailingStop;
        this.trailAtrMult = trailAtrMult;
        this.trailSwing = trailSwing;
        this.bosReentry = bosReentry;
        this.obMaxAge = obMaxAge;
        this.fvgMaxAge = fvgMaxAge;

        if (variantName != null) {
            this.name = variantName;
            this.description = variantName;
        }
        resetState();
    }

    public EnhancedSMCStrategy() {
        this(15, 5, 14, 3.0, 0,
             false, 14, 20,
             false, 20, 1.0,
             false, 12, 26, 9,
             false, 50,
             false, 3.0, false,
             false, 30, 20, null);
    }

    public EnhancedSMCStrategy(Map<String, Object> params) {
        this(
            getInt(params, "swing_left", 15),
            getInt(params, "swing_right", 5),
            getInt(params, "atr_period", 14),
            getDouble(params, "sl_atr_mult", 3.0),
            getDouble(params, "tp_rr", 0),
            getBool(params, "adx_filter", false),
            getInt(params, "adx_period", 14),
            getDouble(params, "adx_threshold", 20),
            getBool(params, "volume_filter", false),
            getInt(params, "vol_sma_period", 20),
            getDouble(params, "vol_mult", 1.0),
            getBool(params, "macd_filter", false),
            getInt(params, "macd_fast", 12),
            getInt(params, "macd_slow", 26),
            getInt(params, "macd_signal", 9),
            getBool(params, "ema_filter", false),
            getInt(params, "ema_period", 50),
            getBool(params, "trailing_stop", false),
            getDouble(params, "trail_atr_mult", 3.0),
            getBool(params, "trail_swing", false),
            getBool(params, "bos_reentry", false),
            getInt(params, "ob_max_age", 30),
            getInt(params, "fvg_max_age", 20),
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
        trailHigh = 0;
        entryBar = -1;
    }

    @Override public String getName() { return name; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public String getDescription() { return description; }
    @Override public void reset() { resetState(); }

    @Override
    public StrategyData computeIndicators(List<Bar> bars) {
        StrategyData data = new StrategyData(bars);
        double[] h = data.high(), l = data.low(), c = data.close();

        // ATR
        data.put("ATR", Indicators.atr(h, l, c, atrPeriod));

        // ADX
        if (adxFilter) {
            data.put("ADX", Indicators.adx(h, l, c, adxPeriod));
        }

        // Volume SMA
        if (volumeFilter) {
            data.put("Vol_SMA", Indicators.volumeSma(data.volume(), volSmaPeriod));
        }

        // MACD
        if (macdFilter) {
            data.put("MACD_hist", Indicators.macdHistogram(c, macdFast, macdSlow, macdSignal));
        }

        // EMA
        if (emaFilter) {
            data.put("EMA", Indicators.emaAdjustFalse(c, emaPeriod));
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

        // Position state transitions: capture next-bar-open fill, reset on exit
        boolean hasPosition = engine.getPosition() != null;
        if (!hasPosition && trailHigh != 0) {
            trailHigh = 0;
        }
        if (hasPosition && trailHigh == 0) {
            trailHigh = engine.getPosition().getEntryPrice();
        }

        // 1. Detect confirmed swing points
        int pivot = i - swingRight;
        boolean isSh = detectSwingHigh(high, pivot);
        if (isSh) { lastSh = new int[]{pivot}; lastShPrice = high[pivot]; }

        boolean isSlPt = detectSwingLow(low, pivot);
        if (isSlPt) { lastSl = new int[]{pivot}; lastSlPrice = low[pivot]; }

        // 2. Detect FVG
        if (i >= 2) {
            if (low[i] > high[i - 2]) activeFvgs.add(new double[]{i, low[i], high[i - 2], 1});
            if (high[i] < low[i - 2]) activeFvgs.add(new double[]{i, low[i - 2], high[i], -1});
        }
        activeFvgs.removeIf(f -> i - (int) f[0] > fvgMaxAge);
        activeObs.removeIf(ob -> i - (int) ob[0] > obMaxAge);

        // 3. Break of Structure
        boolean bullishBos = false;
        if (lastSh != null && close[i] > lastShPrice && structure != 1) {
            bullishBos = true;
            structure = 1;
            bosBbar = i;
            if (lastSl != null) { structLow = new int[]{lastSl[0]}; structLowPrice = lastSlPrice; }
            int searchFrom = Math.max(0, (lastSl != null ? lastSl[0] : i - 20));
            for (int k = i - 1; k >= searchFrom; k--) {
                if (close[k] < open[k]) { activeObs.add(new double[]{k, open[k], low[k]}); break; }
            }
        }

        if (lastSl != null && close[i] < lastSlPrice && structure != -1) {
            structure = -1;
            bosBbar = i;
            stoppedOut = false;
            if (engine.getPosition() != null) engine.closeAll("Bearish BOS");
            activeObs.clear();
        }

        // 3b. Trailing stop
        if (trailingStop && engine.getPosition() != null) {
            if (trailSwing) {
                if (isSlPt && lastSl != null && engine.getPosition().getStopLoss() != null) {
                    double newSl = lastSlPrice - slAtrMult * atrVal;
                    if (newSl > engine.getPosition().getStopLoss()) {
                        engine.getPosition().setStopLoss(newSl);
                    }
                }
            } else {
                if (close[i] > trailHigh) trailHigh = close[i];
                double newTrailSl = trailHigh - trailAtrMult * atrVal;
                if (engine.getPosition().getStopLoss() != null
                        && newTrailSl > engine.getPosition().getStopLoss()) {
                    engine.getPosition().setStopLoss(newTrailSl);
                }
            }
        }

        // Track stopouts
        if (!stoppedOut && structure == 1 && engine.getPosition() == null && bosBbar < i) {
            var trades = engine.getClosedTrades();
            if (!trades.isEmpty()) {
                String ec = trades.get(trades.size() - 1).getExitComment();
                if ("Stop Loss".equals(ec) || "Take Profit".equals(ec)) stoppedOut = true;
            }
        }

        if (!tradeActive) return;

        // 4. Entry logic
        if (structure != 1 || engine.getPosition() != null) return;

        // Filters
        if (adxFilter) {
            double adx = data.get("ADX")[i];
            if (Double.isNaN(adx) || adx < adxThreshold) return;
        }
        if (emaFilter) {
            double ema = data.get("EMA")[i];
            if (Double.isNaN(ema) || close[i] < ema) return;
        }
        if (volumeFilter) {
            double vol = data.volume()[i];
            double volSma = data.get("Vol_SMA")[i];
            if (Double.isNaN(volSma) || vol < volMult * volSma) return;
        }
        if (macdFilter) {
            double macdH = data.get("MACD_hist")[i];
            if (Double.isNaN(macdH) || macdH <= 0) return;
        }

        // BOS entry
        if (bosBbar == i) {
            doEntry(engine, close[i], atrVal, "BOS Entry");
        } else if (bosReentry && stoppedOut) {
            if (tryObEntry(engine, close[i], low[i], atrVal)) stoppedOut = false;
        }
    }

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

    private void doEntry(TradingViewEngine engine, double price, double atrVal, String comment) {
        double sl;
        if (structLow != null) {
            sl = structLowPrice - slAtrMult * atrVal;
        } else {
            sl = price - 3.0 * atrVal;
        }
        if (sl >= price) return;

        Double tp = null;
        if (tpRr > 0) {
            double risk = price - sl;
            tp = price + risk * tpRr;
        }

        engine.entry("Long", Direction.LONG, sl, tp, comment);
        stoppedOut = false;
        // trailHigh set on next bar (actual fill) at top of onBar
    }

    private boolean tryObEntry(TradingViewEngine engine, double closePrice, double lowPrice, double atrVal) {
        Iterator<double[]> it = activeObs.iterator();
        while (it.hasNext()) {
            double[] ob = it.next();
            if (lowPrice <= ob[1] && closePrice > ob[2]) {
                double sl;
                if (structLow != null) {
                    sl = ob[2] - slAtrMult * atrVal;
                } else {
                    sl = closePrice - 3.0 * atrVal;
                }
                if (sl >= closePrice) continue;
                Double tp = null;
                if (tpRr > 0) {
                    double risk = closePrice - sl;
                    tp = closePrice + risk * tpRr;
                }
                engine.entry("Long", Direction.LONG, sl, tp, "OB ReEntry");
                // trailHigh set on next bar (actual fill) at top of onBar
                it.remove();
                return true;
            }
        }
        return false;
    }
}
