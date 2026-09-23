package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.indicator.Indicators;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.stock.analyzer.backtest.strategy.SMCStrategy.*;

/**
 * Quant Trend Strategy — Timeframe: 1D (Daily)
 *
 * 7 Research-backed strategy types:
 * 1. Donchian Breakout (Turtle Trading)
 * 2. Keltner Squeeze (TTM Squeeze)
 * 3. Heikin Ashi Trend
 * 4. Chandelier Exit
 * 5. Mean Reversion in Trend
 * 6. Dual Momentum
 * 7. Volatility Breakout
 */
public class QuantStrategy implements Strategy {

    private String name = "Dual Momentum 252";
    private final String timeframe = "1D";
    private String description = "Dual Momentum (252-day ROC crossover) — SL 2×ATR, TP 4×ATR";

    // Strategy type
    private final String strategyType;
    // Donchian
    private final int donchianEntry, donchianExit;
    // Keltner Squeeze
    private final int kcLen, bbLen;
    private final double kcMult, bbMult;
    // Chandelier
    private final int chandelierPeriod;
    private final double chandelierMult;
    // Mean Reversion
    private final int mrRsiPeriod, mrTrendEma;
    private final double mrRsiEntry, mrRsiExit;
    // Dual Momentum
    private final int momLookback, momEma;
    // Volatility Breakout
    private final double vbMult;
    private final int vbLookback;
    // Common
    private final int atrPeriod;
    private final double slAtrMult, tpAtrMult;
    private final int trendEma;

    // State
    private boolean prevHaBullish;
    private boolean prevSqueeze;
    private double prevMomentum;
    private double highestSinceEntry;
    private boolean inPosition;
    private double prevRsi;
    private double prevMom;
    private final Map<String, Double> prev = new HashMap<>();

    public QuantStrategy(String strategyType,
                          int donchianEntry, int donchianExit,
                          int kcLen, double kcMult, int bbLen, double bbMult,
                          int chandelierPeriod, double chandelierMult,
                          int mrRsiPeriod, double mrRsiEntry, double mrRsiExit, int mrTrendEma,
                          int momLookback, int momEma,
                          double vbMult, int vbLookback,
                          int atrPeriod, double slAtrMult, double tpAtrMult,
                          int trendEma,
                          String variantName) {
        this.strategyType = strategyType;
        this.donchianEntry = donchianEntry; this.donchianExit = donchianExit;
        this.kcLen = kcLen; this.kcMult = kcMult; this.bbLen = bbLen; this.bbMult = bbMult;
        this.chandelierPeriod = chandelierPeriod; this.chandelierMult = chandelierMult;
        this.mrRsiPeriod = mrRsiPeriod; this.mrRsiEntry = mrRsiEntry; this.mrRsiExit = mrRsiExit; this.mrTrendEma = mrTrendEma;
        this.momLookback = momLookback; this.momEma = momEma;
        this.vbMult = vbMult; this.vbLookback = vbLookback;
        this.atrPeriod = atrPeriod; this.slAtrMult = slAtrMult; this.tpAtrMult = tpAtrMult;
        this.trendEma = trendEma;

        if (variantName != null) { this.name = variantName; this.description = variantName; }
        resetState();
    }

    public QuantStrategy() {
        this("dual_mom", 20, 10, 20, 1.5, 20, 2.0, 22, 3.0,
             3, 10, 70, 200, 252, 10, 0.6, 20, 14, 2.0, 4.0, 0, null);
    }

    public QuantStrategy(Map<String, Object> params) {
        this(
            getString(params, "strategy_type", "dual_mom"),
            getInt(params, "donchian_entry", 20), getInt(params, "donchian_exit", 10),
            getInt(params, "kc_len", 20), getDouble(params, "kc_mult", 1.5),
            getInt(params, "bb_len", 20), getDouble(params, "bb_mult", 2.0),
            getInt(params, "chandelier_period", 22), getDouble(params, "chandelier_mult", 3.0),
            getInt(params, "mr_rsi_period", 3), getDouble(params, "mr_rsi_entry", 10),
            getDouble(params, "mr_rsi_exit", 70), getInt(params, "mr_trend_ema", 200),
            getInt(params, "mom_lookback", 252), getInt(params, "mom_ema", 10),
            getDouble(params, "vb_mult", 0.6), getInt(params, "vb_lookback", 20),
            getInt(params, "atr_period", 14), getDouble(params, "sl_atr_mult", 2.0),
            getDouble(params, "tp_atr_mult", 4.0), getInt(params, "trend_ema", 0),
            getString(params, "variant_name", null)
        );
    }

    private void resetState() {
        prevHaBullish = false; prevSqueeze = false; prevMomentum = 0;
        highestSinceEntry = 0; inPosition = false; prevRsi = Double.NaN; prevMom = 0;
        prev.clear();
    }

    @Override public String getName() { return name; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public String getDescription() { return description; }
    @Override public void reset() { resetState(); }

    @Override
    public StrategyData computeIndicators(List<Bar> bars) {
        StrategyData data = new StrategyData(bars);
        double[] h = data.high(), l = data.low(), c = data.close(), o = data.open();

        data.put("ATR", Indicators.atr(h, l, c, atrPeriod));

        switch (strategyType) {
            case "donchian": {
                double[][] dc = Indicators.donchian(h, l, donchianEntry, donchianExit);
                data.put("DC_Entry", dc[0]);
                data.put("DC_Exit", dc[1]);
                break;
            }
            case "squeeze": {
                double[][] sq = Indicators.keltnerSqueeze(h, l, c, kcLen, kcMult, bbLen, bbMult);
                data.put("Squeeze_On", sq[0]);
                data.put("Squeeze_Mom", sq[1]);
                data.put("KC_Mid", sq[2]);
                break;
            }
            case "heikin_ashi": {
                double[][] ha = Indicators.heikinAshi(o, h, l, c);
                data.put("HA_Open", ha[0]);
                data.put("HA_High", ha[1]);
                data.put("HA_Low", ha[2]);
                data.put("HA_Close", ha[3]);
                break;
            }
            case "chandelier": {
                data.put("Chandelier", Indicators.chandelierExit(h, l, c, chandelierPeriod, chandelierMult));
                data.put("EMA_Fast", Indicators.ema(c, 10));
                data.put("EMA_Slow", Indicators.ema(c, chandelierPeriod));
                break;
            }
            case "mean_revert": {
                data.put("MR_RSI", Indicators.rsi(c, mrRsiPeriod));
                data.put("MR_EMA", Indicators.ema(c, mrTrendEma));
                break;
            }
            case "dual_mom": {
                data.put("ROC", Indicators.roc(c, momLookback));
                double[] roc = data.get("ROC");
                double[] rocClean = new double[roc.length];
                for (int i = 0; i < roc.length; i++) rocClean[i] = Double.isNaN(roc[i]) ? 0 : roc[i];
                data.put("ROC_EMA", Indicators.ema(rocClean, momEma));
                data.put("MOM_EMA", Indicators.ema(c, momLookback));
                break;
            }
            case "vol_breakout": {
                int n = c.length;
                double[] dayRange = new double[n];
                for (int i = 0; i < n; i++) dayRange[i] = h[i] - l[i];
                double[] prevRange = new double[n];
                prevRange[0] = Double.NaN;
                for (int i = 1; i < n; i++) prevRange[i] = dayRange[i - 1];
                data.put("Prev_Range", prevRange);
                data.put("Avg_Range", Indicators.sma(dayRange, vbLookback));
                data.put("VB_EMA", Indicators.ema(c, 20));
                break;
            }
        }

        if (trendEma > 0) {
            data.put("Trend_EMA", Indicators.ema(c, trendEma));
        }
        return data;
    }

    @Override
    public void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive) {
        if (i < 2) { updateState(data, i); return; }

        double atr = data.get("ATR")[i];
        double close = data.close()[i];
        double highVal = data.high()[i];
        double lowVal = data.low()[i];

        // Track highest since entry
        if (engine.getPosition() != null) {
            inPosition = true;
            highestSinceEntry = Math.max(highestSinceEntry, highVal);
        } else if (inPosition) {
            inPosition = false;
            highestSinceEntry = 0;
        }

        // Trend filter
        if (trendEma > 0 && data.has("Trend_EMA")) {
            double te = data.get("Trend_EMA")[i];
            if (!Double.isNaN(te) && close < te) {
                handleExit(engine, data, i);
                updateState(data, i);
                return;
            }
        }

        switch (strategyType) {
            case "donchian": onBarDonchian(engine, data, i, tradeActive, close, atr); break;
            case "squeeze": onBarSqueeze(engine, data, i, tradeActive, close, atr); break;
            case "heikin_ashi": onBarHeikinAshi(engine, data, i, tradeActive, close, atr); break;
            case "chandelier": onBarChandelier(engine, data, i, tradeActive, close, atr); break;
            case "mean_revert": onBarMeanRevert(engine, data, i, tradeActive, close, atr); break;
            case "dual_mom": onBarDualMom(engine, data, i, tradeActive, close, atr); break;
            case "vol_breakout": onBarVolBreakout(engine, data, i, tradeActive, close, atr); break;
        }

        updateState(data, i);
    }

    private void handleExit(TradingViewEngine engine, StrategyData data, int i) {
        if (engine.getPosition() == null) return;
        double close = data.close()[i];
        switch (strategyType) {
            case "donchian": {
                double el = data.get("DC_Exit")[i];
                if (!Double.isNaN(el) && close < el) engine.closeAll("DC Exit Low");
                break;
            }
            case "heikin_ashi": {
                double hc = data.get("HA_Close")[i], ho = data.get("HA_Open")[i];
                if (hc < ho && prevHaBullish) engine.closeAll("HA Bearish");
                break;
            }
            case "chandelier": {
                double ch = data.get("Chandelier")[i];
                if (!Double.isNaN(ch) && close < ch) engine.closeAll("Chandelier Exit");
                break;
            }
            case "mean_revert": {
                double rsi = data.get("MR_RSI")[i];
                if (!Double.isNaN(rsi) && rsi > mrRsiExit) engine.closeAll("MR RSI Exit");
                break;
            }
            case "squeeze": {
                double mom = data.get("Squeeze_Mom")[i];
                if (!Double.isNaN(mom) && !Double.isNaN(prevMomentum) && mom < prevMomentum && mom < 0)
                    engine.closeAll("Squeeze Mom Exit");
                break;
            }
            case "dual_mom": {
                double roc = data.get("ROC")[i];
                if (!Double.isNaN(roc) && roc < 0) engine.closeAll("Mom Exit");
                break;
            }
        }
    }

    // ── Donchian Breakout ─────────────────────────────────────────

    private void onBarDonchian(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        if (engine.getPosition() != null) {
            double el = data.get("DC_Exit")[i];
            if (!Double.isNaN(el) && close < el) engine.closeAll("DC Exit Low");
        }
        if (ta && engine.getPosition() == null) {
            double eh = data.get("DC_Entry")[i];
            if (!Double.isNaN(eh) && !Double.isNaN(atr) && close > eh) {
                Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "DC Breakout");
            }
        }
    }

    // ── Keltner Squeeze ───────────────────────────────────────────

    private void onBarSqueeze(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double mom = data.get("Squeeze_Mom")[i];
        if (engine.getPosition() != null) {
            if (!Double.isNaN(mom) && !Double.isNaN(prevMomentum) && mom < 0 && mom < prevMomentum)
                engine.closeAll("Squeeze Mom Exit");
        }
        if (ta && engine.getPosition() == null) {
            boolean sqOn = data.get("Squeeze_On")[i] == 1.0;
            if (prevSqueeze && !sqOn && !Double.isNaN(mom) && mom > 0
                    && !Double.isNaN(prevMomentum) && mom > prevMomentum && !Double.isNaN(atr)) {
                Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "Squeeze Fire");
            }
        }
    }

    // ── Heikin Ashi Trend ─────────────────────────────────────────

    private void onBarHeikinAshi(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double hc = data.get("HA_Close")[i], ho = data.get("HA_Open")[i];
        boolean bullish = hc > ho;
        if (engine.getPosition() != null) {
            if (!bullish && prevHaBullish) engine.closeAll("HA Bearish");
        }
        if (ta && engine.getPosition() == null) {
            if (bullish && !prevHaBullish && !Double.isNaN(atr)) {
                Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "HA Bullish");
            }
        }
    }

    // ── Chandelier Exit ───────────────────────────────────────────

    private void onBarChandelier(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double ch = data.get("Chandelier")[i];
        double ef = data.get("EMA_Fast")[i];
        double es = data.get("EMA_Slow")[i];

        if (engine.getPosition() != null) {
            if (!Double.isNaN(ch) && close < ch) engine.closeAll("Chandelier Exit");
        }
        if (ta && engine.getPosition() == null) {
            double pf = prev.getOrDefault("ema_fast", Double.NaN);
            double ps = prev.getOrDefault("ema_slow", Double.NaN);
            if (!Double.isNaN(ef) && !Double.isNaN(pf) && pf <= ps && ef > es
                    && !Double.isNaN(ch) && close > ch && !Double.isNaN(atr)) {
                Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "Chandelier Entry");
            }
        }
    }

    // ── Mean Reversion in Trend ───────────────────────────────────

    private void onBarMeanRevert(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double rsi = data.get("MR_RSI")[i];
        double ema = data.get("MR_EMA")[i];

        if (engine.getPosition() != null) {
            if (!Double.isNaN(rsi) && !Double.isNaN(prevRsi) && prevRsi < mrRsiExit && rsi >= mrRsiExit)
                engine.closeAll("MR RSI Exit");
        }
        if (ta && engine.getPosition() == null) {
            if (!Double.isNaN(rsi) && !Double.isNaN(ema) && close > ema && rsi <= mrRsiEntry && !Double.isNaN(atr)) {
                Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "MR Entry");
            }
        }
    }

    // ── Dual Momentum ─────────────────────────────────────────────

    private void onBarDualMom(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double roc = data.get("ROC")[i];
        double rocEma = data.get("ROC_EMA")[i];
        double momEmaVal = data.get("MOM_EMA")[i];

        if (engine.getPosition() != null) {
            if (!Double.isNaN(roc) && roc < 0) engine.closeAll("Mom Negative");
            else if (!Double.isNaN(momEmaVal) && close < momEmaVal) engine.closeAll("Below Mom EMA");
        }
        if (ta && engine.getPosition() == null) {
            if (!Double.isNaN(roc) && roc > 0
                    && !Double.isNaN(rocEma) && roc > rocEma
                    && !Double.isNaN(momEmaVal) && close > momEmaVal
                    && !Double.isNaN(prevMom)
                    && (prevMom <= 0 || rocEma <= 0)) {
                double pRoc = prev.getOrDefault("roc", Double.NaN);
                double pRocEma = prev.getOrDefault("roc_ema", Double.NaN);
                if (!Double.isNaN(pRoc) && !Double.isNaN(pRocEma)
                        && pRoc <= pRocEma && roc > rocEma
                        && !Double.isNaN(atr)) {
                    Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                    Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                    if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "Dual Mom Entry");
                }
            }
        }
    }

    // ── Volatility Breakout ───────────────────────────────────────

    private void onBarVolBreakout(TradingViewEngine engine, StrategyData data, int i, boolean ta, double close, double atr) {
        double vbEma = data.get("VB_EMA")[i];
        double prevRange = data.get("Prev_Range")[i];
        double avgRange = data.get("Avg_Range")[i];
        double openVal = data.open()[i];
        double highVal = data.high()[i];

        if (engine.getPosition() != null) {
            if (!Double.isNaN(vbEma) && close < vbEma) engine.closeAll("VB Below EMA");
        }
        if (ta && engine.getPosition() == null) {
            if (!Double.isNaN(prevRange) && !Double.isNaN(avgRange)
                    && !Double.isNaN(vbEma) && close > vbEma) {
                double breakoutLevel = openVal + vbMult * prevRange;
                if (highVal > breakoutLevel && prevRange > avgRange * 0.5 && !Double.isNaN(atr)) {
                    Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
                    Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
                    if (sl == null || sl < close) engine.entry("Long", Direction.LONG, sl, tp, "VB Breakout");
                }
            }
        }
    }

    // ── State management ──────────────────────────────────────────

    private void updateState(StrategyData data, int i) {
        if (data.has("HA_Close")) {
            double hc = data.get("HA_Close")[i], ho = data.get("HA_Open")[i];
            prevHaBullish = hc > ho;
        }
        if (data.has("Squeeze_On")) {
            prevSqueeze = data.get("Squeeze_On")[i] == 1.0;
            double m = data.get("Squeeze_Mom")[i];
            prevMomentum = Double.isNaN(m) ? 0 : m;
        }
        if (data.has("MR_RSI")) {
            prevRsi = data.get("MR_RSI")[i];
        }
        if (data.has("ROC")) {
            double r = data.get("ROC")[i];
            prevMom = Double.isNaN(r) ? 0 : r;
            prev.put("roc", r);
            prev.put("roc_ema", data.get("ROC_EMA")[i]);
        }
        if (data.has("EMA_Fast")) prev.put("ema_fast", data.get("EMA_Fast")[i]);
        if (data.has("EMA_Slow")) prev.put("ema_slow", data.get("EMA_Slow")[i]);
    }
}
