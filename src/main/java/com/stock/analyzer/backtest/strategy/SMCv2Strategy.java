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
 * SMC v2 Strategy — Timeframe: 1D (Daily)
 *
 * Data-driven improvements over Enhanced SMC:
 * 1. ATR trailing stop with activation threshold
 * 2. BB width filter — skip tight squeeze entries
 * 3. RSI momentum filter
 * 4. Min structure age filter
 * 5. Stricter ADX threshold
 */
public class SMCv2Strategy implements Strategy {

    private String name = "SMC v2";
    private final String timeframe = "1D";
    private String description = "SMC v2 with trailing stop, BB/RSI/ADX filters, re-entry";

    // Swing structure
    private final int swingLeft, swingRight, atrPeriod;
    // SL / TP
    private final double slAtrMult, tpRr;
    // ATR Trailing Stop
    private final boolean trailingStop;
    private final double trailAtrMult, trailActivationPct;
    // ADX filter
    private final boolean adxFilter;
    private final int adxPeriod;
    private final double adxThreshold;
    // BB Width filter
    private final boolean bbFilter;
    private final int bbPeriod;
    private final double bbStd, bbMinWidth;
    // RSI filter
    private final boolean rsiFilter;
    private final int rsiPeriod;
    private final double rsiMin;
    // Volume filter
    private final boolean volumeFilter;
    private final int volSmaPeriod;
    private final double volMult;
    // MACD filter
    private final boolean macdFilter;
    private final int macdFast, macdSlow, macdSignal;
    // EMA filter
    private final boolean emaFilter;
    private final int emaPeriod;
    // Re-entry / min bars
    private final boolean bosReentry;
    private final int minBarsSinceSl;
    // OB/FVG
    private final int obMaxAge, fvgMaxAge;

    // State
    private int[] lastSh; private double lastShPrice;
    private int[] lastSl; private double lastSlPrice;
    private int structure;
    private int[] structLow; private double structLowPrice;
    private List<double[]> activeObs;
    private List<double[]> activeFvgs;
    private int bosBbar;
    private boolean stoppedOut;
    private double trailHigh, entryPrice;
    private int entryBar;

    public SMCv2Strategy(int swingLeft, int swingRight, int atrPeriod,
                          double slAtrMult, double tpRr,
                          boolean trailingStop, double trailAtrMult, double trailActivationPct,
                          boolean adxFilter, int adxPeriod, double adxThreshold,
                          boolean bbFilter, int bbPeriod, double bbStd, double bbMinWidth,
                          boolean rsiFilter, int rsiPeriod, double rsiMin,
                          boolean volumeFilter, int volSmaPeriod, double volMult,
                          boolean macdFilter, int macdFast, int macdSlow, int macdSignal,
                          boolean emaFilter, int emaPeriod,
                          boolean bosReentry, int minBarsSinceSl,
                          int obMaxAge, int fvgMaxAge,
                          String variantName) {
        this.swingLeft = swingLeft; this.swingRight = swingRight; this.atrPeriod = atrPeriod;
        this.slAtrMult = slAtrMult; this.tpRr = tpRr;
        this.trailingStop = trailingStop; this.trailAtrMult = trailAtrMult; this.trailActivationPct = trailActivationPct;
        this.adxFilter = adxFilter; this.adxPeriod = adxPeriod; this.adxThreshold = adxThreshold;
        this.bbFilter = bbFilter; this.bbPeriod = bbPeriod; this.bbStd = bbStd; this.bbMinWidth = bbMinWidth;
        this.rsiFilter = rsiFilter; this.rsiPeriod = rsiPeriod; this.rsiMin = rsiMin;
        this.volumeFilter = volumeFilter; this.volSmaPeriod = volSmaPeriod; this.volMult = volMult;
        this.macdFilter = macdFilter; this.macdFast = macdFast; this.macdSlow = macdSlow; this.macdSignal = macdSignal;
        this.emaFilter = emaFilter; this.emaPeriod = emaPeriod;
        this.bosReentry = bosReentry; this.minBarsSinceSl = minBarsSinceSl;
        this.obMaxAge = obMaxAge; this.fvgMaxAge = fvgMaxAge;

        if (variantName != null) { this.name = variantName; this.description = variantName; }
        resetState();
    }

    public SMCv2Strategy() {
        this(15, 3, 14, 3.0, 0,
             true, 4.0, 2.0,
             true, 14, 15,
             false, 20, 2.0, 0,
             false, 14, 50,
             false, 20, 1.0,
             false, 12, 26, 9,
             false, 50,
             true, 0,
             30, 20, null);
    }

    public SMCv2Strategy(Map<String, Object> params) {
        this(
            getInt(params, "swing_left", 15), getInt(params, "swing_right", 3), getInt(params, "atr_period", 14),
            getDouble(params, "sl_atr_mult", 3.0), getDouble(params, "tp_rr", 0),
            getBool(params, "trailing_stop", true), getDouble(params, "trail_atr_mult", 4.0), getDouble(params, "trail_activation_pct", 2.0),
            getBool(params, "adx_filter", true), getInt(params, "adx_period", 14), getDouble(params, "adx_threshold", 15),
            getBool(params, "bb_filter", false), getInt(params, "bb_period", 20), getDouble(params, "bb_std", 2.0), getDouble(params, "bb_min_width", 0),
            getBool(params, "rsi_filter", false), getInt(params, "rsi_period", 14), getDouble(params, "rsi_min", 50),
            getBool(params, "volume_filter", false), getInt(params, "vol_sma_period", 20), getDouble(params, "vol_mult", 1.0),
            getBool(params, "macd_filter", false), getInt(params, "macd_fast", 12), getInt(params, "macd_slow", 26), getInt(params, "macd_signal", 9),
            getBool(params, "ema_filter", false), getInt(params, "ema_period", 50),
            getBool(params, "bos_reentry", true), getInt(params, "min_bars_since_sl", 0),
            getInt(params, "ob_max_age", 30), getInt(params, "fvg_max_age", 20),
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
        bosBbar = -1; stoppedOut = false;
        trailHigh = 0; entryPrice = 0; entryBar = -1;
    }

    @Override public String getName() { return name; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public String getDescription() { return description; }
    @Override public void reset() { resetState(); }

    @Override
    public StrategyData computeIndicators(List<Bar> bars) {
        StrategyData data = new StrategyData(bars);
        double[] h = data.high(), l = data.low(), c = data.close();
        data.put("ATR", Indicators.atr(h, l, c, atrPeriod));
        if (adxFilter) data.put("ADX", Indicators.adx(h, l, c, adxPeriod));
        if (bbFilter) data.put("BB_Width", Indicators.bbWidth(c, bbPeriod, bbStd));
        if (rsiFilter) data.put("RSI", Indicators.rsi(c, rsiPeriod));
        if (volumeFilter) data.put("Vol_SMA", Indicators.volumeSma(data.volume(), volSmaPeriod));
        if (macdFilter) data.put("MACD_hist", Indicators.macdHistogram(c, macdFast, macdSlow, macdSignal));
        if (emaFilter) data.put("EMA", Indicators.emaAdjustFalse(c, emaPeriod));
        return data;
    }

    @Override
    public void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive) {
        int minBars = swingLeft + swingRight + 1;
        if (i < minBars) return;

        double[] high = data.high(), low = data.low(), close = data.close(), open = data.open();
        double atrVal = data.get("ATR")[i];
        if (Double.isNaN(atrVal) || atrVal <= 0) return;

        // Position state transitions: capture next-bar-open fill, reset on exit
        boolean hasPosition = engine.getPosition() != null;
        if (!hasPosition && entryPrice != 0) {
            entryPrice = 0;
            trailHigh = 0;
        }
        if (hasPosition && entryPrice == 0) {
            entryPrice = engine.getPosition().getEntryPrice();
            trailHigh = entryPrice;
        }

        // 1. Swing points
        int pivot = i - swingRight;
        if (detectSwingHigh(high, pivot)) { lastSh = new int[]{pivot}; lastShPrice = high[pivot]; }
        boolean isSlPt = detectSwingLow(low, pivot);
        if (isSlPt) { lastSl = new int[]{pivot}; lastSlPrice = low[pivot]; }

        // 2. FVG
        if (i >= 2) {
            if (low[i] > high[i - 2]) activeFvgs.add(new double[]{i, low[i], high[i - 2], 1});
            if (high[i] < low[i - 2]) activeFvgs.add(new double[]{i, low[i - 2], high[i], -1});
        }
        activeFvgs.removeIf(f -> i - (int) f[0] > fvgMaxAge);
        activeObs.removeIf(ob -> i - (int) ob[0] > obMaxAge);

        // 3. BOS
        if (lastSh != null && close[i] > lastShPrice && structure != 1) {
            structure = 1; bosBbar = i;
            if (lastSl != null) { structLow = new int[]{lastSl[0]}; structLowPrice = lastSlPrice; }
            int searchFrom = Math.max(0, (lastSl != null ? lastSl[0] : i - 20));
            for (int k = i - 1; k >= searchFrom; k--) {
                if (close[k] < open[k]) { activeObs.add(new double[]{k, open[k], low[k]}); break; }
            }
        }
        if (lastSl != null && close[i] < lastSlPrice && structure != -1) {
            structure = -1; bosBbar = i; stoppedOut = false;
            if (engine.getPosition() != null) engine.closeAll("Bearish BOS");
            activeObs.clear();
        }

        // 3b. Trailing stop with activation threshold
        if (trailingStop && engine.getPosition() != null) {
            if (close[i] > trailHigh) trailHigh = close[i];
            if (entryPrice > 0) {
                double profitPct = (trailHigh - entryPrice) / entryPrice * 100;
                if (profitPct >= trailActivationPct) {
                    double newTrailSl = trailHigh - trailAtrMult * atrVal;
                    if (engine.getPosition().getStopLoss() != null
                            && newTrailSl > engine.getPosition().getStopLoss()) {
                        engine.getPosition().setStopLoss(newTrailSl);
                    }
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
        if (adxFilter) { double v = data.get("ADX")[i]; if (Double.isNaN(v) || v < adxThreshold) return; }
        if (bbFilter && bbMinWidth > 0) { double v = data.get("BB_Width")[i]; if (Double.isNaN(v) || v < bbMinWidth) return; }
        if (rsiFilter) { double v = data.get("RSI")[i]; if (Double.isNaN(v) || v < rsiMin) return; }
        if (emaFilter) { double v = data.get("EMA")[i]; if (Double.isNaN(v) || close[i] < v) return; }
        if (volumeFilter) { double vol = data.volume()[i]; double vs = data.get("Vol_SMA")[i]; if (Double.isNaN(vs) || vol < volMult * vs) return; }
        if (macdFilter) { double v = data.get("MACD_hist")[i]; if (Double.isNaN(v) || v <= 0) return; }
        if (minBarsSinceSl > 0 && structLow != null) {
            int barsSince = i - structLow[0];
            if (barsSince < minBarsSinceSl) return;
        }

        // BOS entry
        if (bosBbar == i) {
            doEntry(engine, close[i], atrVal, "BOS Entry");
        } else if (bosReentry && stoppedOut) {
            if (tryObEntry(engine, close[i], low[i], atrVal)) stoppedOut = false;
        }
    }

    private boolean detectSwingHigh(double[] high, int pivot) {
        for (int j = 1; j <= swingLeft; j++) { if (pivot - j < 0 || high[pivot] <= high[pivot - j]) return false; }
        for (int j = 1; j <= swingRight; j++) { if (high[pivot] <= high[pivot + j]) return false; }
        return true;
    }

    private boolean detectSwingLow(double[] low, int pivot) {
        for (int j = 1; j <= swingLeft; j++) { if (pivot - j < 0 || low[pivot] >= low[pivot - j]) return false; }
        for (int j = 1; j <= swingRight; j++) { if (low[pivot] >= low[pivot + j]) return false; }
        return true;
    }

    private void doEntry(TradingViewEngine engine, double price, double atrVal, String comment) {
        double sl = structLow != null ? structLowPrice - slAtrMult * atrVal : price - 3.0 * atrVal;
        if (sl >= price) return;
        Double tp = null;
        if (tpRr > 0) tp = price + (price - sl) * tpRr;
        engine.entry("Long", Direction.LONG, sl, tp, comment);
        // entryPrice + trailHigh set on next bar (actual fill) at top of onBar
        stoppedOut = false;
    }

    private boolean tryObEntry(TradingViewEngine engine, double closePrice, double lowPrice, double atrVal) {
        Iterator<double[]> it = activeObs.iterator();
        while (it.hasNext()) {
            double[] ob = it.next();
            if (lowPrice <= ob[1] && closePrice > ob[2]) {
                double sl = structLow != null ? ob[2] - slAtrMult * atrVal : closePrice - 3.0 * atrVal;
                if (sl >= closePrice) continue;
                Double tp = null;
                if (tpRr > 0) tp = closePrice + (closePrice - sl) * tpRr;
                engine.entry("Long", Direction.LONG, sl, tp, "OB ReEntry");
                // entryPrice + trailHigh set on next bar (actual fill) at top of onBar
                it.remove();
                return true;
            }
        }
        return false;
    }
}
