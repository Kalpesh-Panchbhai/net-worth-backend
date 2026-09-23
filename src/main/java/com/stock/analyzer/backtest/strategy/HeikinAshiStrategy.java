package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.indicator.Indicators;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.Direction;

import java.util.List;
import java.util.Map;

import static com.stock.analyzer.backtest.strategy.SMCStrategy.*;

/**
 * Enhanced Heikin Ashi Trend Strategy — Timeframe: 1D (Daily)
 *
 * Three entry modes:
 * A. "flip" — Classic HA bearish-to-bullish flip (original)
 * B. "pullback" — Enter on HA bullish resumption after brief bearish pullback in confirmed uptrend
 * C. "rsi_pullback" — Enter on RSI pullback within HA uptrend (like Mean Rev in Trend but HA-guided)
 *
 * Exit modes (combinable):
 * 1. HA bearish bars (configurable: require N consecutive bearish before exit)
 * 2. Exit EMA — close below exit EMA
 * 3. ATR trailing stop
 * 4. Trend EMA break — close below trend EMA
 */
public class HeikinAshiStrategy implements Strategy {

    private final String name;
    private final String timeframe;

    // ── Entry mode ───────────────────────────────────────────
    private final String entryMode;         // "flip", "pullback", "rsi_pullback", "regime"
    private final int minTrendBars;         // pullback: min bullish bars before pullback allowed
    private final int maxPullbackBars;      // pullback: max bearish bars in a pullback (1-3)
    private final double rsiEntry;          // rsi_pullback/regime: RSI must be < this
    private final int rsiPeriod;
    private final int regimeWindow;         // regime: count bullish bars in last N bars
    private final double regimeThreshold;   // regime: fraction of bars that must be bullish (0.0-1.0)

    // ── Trend filters ────────────────────────────────────────
    private final int trendEmaPeriod;       // 0 = disabled
    private final boolean adxFilter;
    private final double adxThreshold;
    private final int adxPeriod;

    // ── HA entry filters ─────────────────────────────────────
    private final int confirmBars;          // flip mode: require N consecutive bullish bars
    private final boolean strongCandle;     // flip mode: require no lower wick

    // ── Volume filter ────────────────────────────────────────
    private final boolean volumeFilter;
    private final double volMult;
    private final int volPeriod;

    // ── BB width filter (sideways detection) ─────────────────
    private final boolean bbFilter;
    private final double bbMinWidth;

    // ── Exit controls ────────────────────────────────────────
    private final boolean haFlipExit;       // explicit HA flip exit (always fires on bearish transition)
    private final int exitBearishBars;      // require N consecutive bearish HA bars (0 = disabled)
    private final int exitEmaPeriod;        // 0 = disabled
    private final boolean useTrailing;
    private final double trailAtrMult;
    private final double trailActivationPct;

    // ── Risk management ──────────────────────────────────────
    private final double slAtrMult;
    private final double tpAtrMult;
    private final int atrPeriod;

    // ── Loss cooldown ─────────────────────────────────────────
    private final int cooldownBars;         // 0 = disabled; skip N bars after losing trade
    private final int maxConsecLosses;      // 0 = any loss triggers cooldown; N = after N consecutive losses

    // ── EMA slope filter ──────────────────────────────────────
    private final int emaSlopeBars;         // 0 = disabled; require trend EMA rising over N bars

    // ── v2 mechanics: breakout entries / momentum (all default OFF) ──
    private final int donchianPeriod;        // "donchian" entry: close > prior-N high
    private final boolean donchianExit;      // exit on close < prior-N low
    private final int donchianExitPeriod;
    private final boolean adxRising;         // filter: require ADX rising over adxSlope bars
    private final int adxSlope;
    private final double rocMin;             // filter: 0 = off; require ROC(rocPeriod) >= rocMin
    private final int rocPeriod;
    private final int kcEma;                 // "keltner" entry: close > EMA(kcEma) + kcMult*ATR
    private final double kcMult;

    // ── State ────────────────────────────────────────────────
    private int consecutiveBullish;
    private int consecutiveBearish;
    private int peakBullishStreak;          // max bullish streak before current pullback
    private boolean prevHaBullish;
    private double highestSinceEntry;
    private double entryPrice;
    private double trailStop;
    private boolean trailActive;
    private boolean hadPullback;            // for pullback mode: did we see bearish bars after trend?
    private int[] haHistory;                // ring buffer for regime mode (1=bullish, 0=bearish)
    private int haHistoryIdx;
    private int cooldownRemaining;          // bars remaining in cooldown
    private int consecutiveLosses;          // count of consecutive losing trades

    public HeikinAshiStrategy(Map<String, Object> params) {
        this.name = getString(params, "variant_name", "HA Enhanced");
        this.timeframe = getString(params, "timeframe", "1D");

        this.entryMode = getString(params, "entry_mode", "flip");
        this.minTrendBars = getInt(params, "min_trend_bars", 5);
        this.maxPullbackBars = getInt(params, "max_pullback_bars", 2);
        this.rsiEntry = getDouble(params, "rsi_entry", 55.0);
        this.rsiPeriod = getInt(params, "rsi_period", 2);
        this.regimeWindow = getInt(params, "regime_window", 10);
        this.regimeThreshold = getDouble(params, "regime_threshold", 0.6);

        this.trendEmaPeriod = getInt(params, "trend_ema", 0);
        this.adxFilter = getBool(params, "adx_filter", false);
        this.adxThreshold = getDouble(params, "adx_threshold", 20.0);
        this.adxPeriod = getInt(params, "adx_period", 14);

        this.confirmBars = getInt(params, "confirm_bars", 1);
        this.strongCandle = getBool(params, "strong_candle", false);

        this.volumeFilter = getBool(params, "volume_filter", false);
        this.volMult = getDouble(params, "vol_mult", 1.0);
        this.volPeriod = getInt(params, "vol_period", 20);

        this.bbFilter = getBool(params, "bb_filter", false);
        this.bbMinWidth = getDouble(params, "bb_min_width", 6.0);

        this.haFlipExit = getBool(params, "ha_flip_exit", false);
        this.exitBearishBars = getInt(params, "exit_bearish_bars", 0);
        this.exitEmaPeriod = getInt(params, "exit_ema", 0);
        this.useTrailing = getBool(params, "trailing_stop", false);
        this.trailAtrMult = getDouble(params, "trail_atr_mult", 3.0);
        this.trailActivationPct = getDouble(params, "trail_activation_pct", 2.0);

        this.slAtrMult = getDouble(params, "sl_atr_mult", 0.0);
        this.tpAtrMult = getDouble(params, "tp_atr_mult", 0.0);
        this.atrPeriod = getInt(params, "atr_period", 14);

        this.cooldownBars = getInt(params, "cooldown_bars", 0);
        this.maxConsecLosses = getInt(params, "max_consec_losses", 0);
        this.emaSlopeBars = getInt(params, "ema_slope_bars", 0);

        this.donchianPeriod = getInt(params, "donchian_period", 20);
        this.donchianExit = getBool(params, "donchian_exit", false);
        this.donchianExitPeriod = getInt(params, "donchian_exit_period", 10);
        this.adxRising = getBool(params, "adx_rising", false);
        this.adxSlope = getInt(params, "adx_slope", 3);
        this.rocMin = getDouble(params, "roc_min", 0.0);
        this.rocPeriod = getInt(params, "roc_period", 20);
        this.kcEma = getInt(params, "kc_ema", 20);
        this.kcMult = getDouble(params, "kc_mult", 1.5);

        resetState();
    }

    private void resetState() {
        consecutiveBullish = 0;
        consecutiveBearish = 0;
        peakBullishStreak = 0;
        prevHaBullish = false;
        highestSinceEntry = 0;
        entryPrice = 0;
        trailStop = 0;
        trailActive = false;
        hadPullback = false;
        haHistory = new int[Math.max(regimeWindow, 1)];
        haHistoryIdx = 0;
        cooldownRemaining = 0;
        consecutiveLosses = 0;
    }

    @Override public String getName() { return name; }
    @Override public String getTimeframe() { return timeframe; }
    @Override public String getDescription() { return name; }
    @Override public void reset() { resetState(); }

    @Override
    public StrategyData computeIndicators(List<Bar> bars) {
        StrategyData data = new StrategyData(bars);
        double[] h = data.high(), l = data.low(), c = data.close(), o = data.open();

        double[][] ha = Indicators.heikinAshi(o, h, l, c);
        data.put("HA_Open", ha[0]);
        data.put("HA_High", ha[1]);
        data.put("HA_Low", ha[2]);
        data.put("HA_Close", ha[3]);
        data.put("ATR", Indicators.atr(h, l, c, atrPeriod));

        if (trendEmaPeriod > 0) data.put("Trend_EMA", Indicators.ema(c, trendEmaPeriod));
        if (adxFilter) data.put("ADX", Indicators.adx(h, l, c, adxPeriod));
        if (volumeFilter) {
            double[] vol = new double[bars.size()];
            for (int i = 0; i < bars.size(); i++) vol[i] = bars.get(i).getVolume();
            data.put("Vol_SMA", Indicators.sma(vol, volPeriod));
            data.put("Volume", vol);
        }
        if (bbFilter) data.put("BB_Width", Indicators.bbWidth(c, 20, 2.0));
        if (exitEmaPeriod > 0) data.put("Exit_EMA", Indicators.ema(c, exitEmaPeriod));

        // RSI — compute for rsi_pullback or regime mode
        boolean needRsi = "rsi_pullback".equals(entryMode) || "regime".equals(entryMode);
        if (needRsi) data.put("RSI", Indicators.rsi(c, rsiPeriod));

        // ── v2 mechanics (only computed when actually used) ──
        if ("donchian".equals(entryMode) || donchianExit) {
            double[][] don = Indicators.donchian(h, l,
                Math.max(donchianPeriod, 1), Math.max(donchianExitPeriod, 1));
            data.put("Donch_Hi", don[0]);
            data.put("Donch_Lo", don[1]);
        }
        if ("keltner".equals(entryMode)) data.put("KC_Mid", Indicators.ema(c, kcEma));
        if (adxRising && !data.has("ADX")) data.put("ADX", Indicators.adx(h, l, c, adxPeriod));
        if (rocMin != 0.0) data.put("ROC", Indicators.roc(c, Math.max(rocPeriod, 1)));

        return data;
    }

    @Override
    public void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive) {
        if (i < 2) { updateHaState(data, i); return; }

        double close = data.close()[i];
        double high = data.high()[i];
        double atr = data.get("ATR")[i];
        if (Double.isNaN(atr)) { updateHaState(data, i); return; }

        double haClose = data.get("HA_Close")[i];
        double haOpen = data.get("HA_Open")[i];
        double haLow = data.get("HA_Low")[i];
        boolean haBullish = haClose > haOpen;

        boolean inPosition = engine.getPosition() != null;
        // Position state transitions: capture next-bar-open fill, reset on exit (incl. SL/TP)
        if (!inPosition && entryPrice != 0) {
            entryPrice = 0;
            highestSinceEntry = 0;
            trailStop = 0;
            trailActive = false;
        }
        if (inPosition) {
            if (entryPrice == 0) {
                entryPrice = engine.getPosition().getEntryPrice();
                highestSinceEntry = high;
            } else {
                highestSinceEntry = Math.max(highestSinceEntry, high);
            }
            handleExit(engine, data, i, close, high, atr, haBullish);
        } else if (tradeActive) {
            trailStop = 0;
            trailActive = false;
            // Decrement cooldown every bar (not just on entry signals)
            if (cooldownRemaining > 0) {
                cooldownRemaining--;
            } else {
                handleEntry(engine, data, i, close, high, atr, haBullish, haClose, haOpen, haLow);
            }
        }

        updateHaState(data, i);
    }

    // ── EXIT LOGIC ───────────────────────────────────────────

    private void handleExit(TradingViewEngine engine, StrategyData data, int i,
                            double close, double high, double atr, boolean haBullish) {
        boolean exitSignal = false;

        // 0. Explicit HA flip exit (always fires alongside other exits)
        if (haFlipExit && !haBullish && prevHaBullish) exitSignal = true;

        // 1. HA bearish bars — require N consecutive bearish bars
        if (exitBearishBars > 0) {
            if (!haBullish) {
                // consecutiveBearish is from PREVIOUS bars, current bar is also bearish
                if (consecutiveBearish + 1 >= exitBearishBars) exitSignal = true;
            }
        } else if (exitBearishBars == 0) {
            // Legacy: single bearish flip exits (like R1/R2 "ha_exit" mode)
            // Only when no other exit is configured
            if (exitEmaPeriod == 0 && !useTrailing && !haBullish && prevHaBullish) exitSignal = true;
        }

        // 2. Exit EMA
        if (exitEmaPeriod > 0 && data.has("Exit_EMA")) {
            double exitEma = data.get("Exit_EMA")[i];
            if (!Double.isNaN(exitEma) && close < exitEma) exitSignal = true;
        }

        // 3. ATR trailing stop
        if (useTrailing) {
            double profitPct = entryPrice > 0 ? (close - entryPrice) / entryPrice * 100.0 : 0;
            if (profitPct >= trailActivationPct) {
                double newTrail = high - trailAtrMult * atr;
                if (!trailActive || newTrail > trailStop) {
                    trailStop = newTrail;
                    trailActive = true;
                }
            }
            if (trailActive && close < trailStop) exitSignal = true;
        }

        // 4. Trend EMA break
        if (trendEmaPeriod > 0 && data.has("Trend_EMA")) {
            double tEma = data.get("Trend_EMA")[i];
            if (!Double.isNaN(tEma) && close < tEma) exitSignal = true;
        }

        // 5. Donchian channel exit — close below prior-N low
        if (donchianExit && data.has("Donch_Lo")) {
            double dLo = data.get("Donch_Lo")[i];
            if (!Double.isNaN(dLo) && close < dLo) exitSignal = true;
        }

        if (exitSignal) {
            boolean wasLoss = entryPrice > 0 && close < entryPrice;
            engine.closeAll("HA Exit");
            if (wasLoss) {
                consecutiveLosses++;
                if (cooldownBars > 0) {
                    if (maxConsecLosses <= 0 || consecutiveLosses >= maxConsecLosses) {
                        cooldownRemaining = cooldownBars;
                    }
                }
            } else {
                consecutiveLosses = 0;
            }
            highestSinceEntry = 0;
            entryPrice = 0;
            trailStop = 0;
            trailActive = false;
        }
    }

    // ── ENTRY LOGIC ──────────────────────────────────────────

    private void handleEntry(TradingViewEngine engine, StrategyData data, int i,
                             double close, double high, double atr,
                             boolean haBullish, double haClose, double haOpen, double haLow) {

        boolean entryOk;
        switch (entryMode) {
            case "pullback":
                entryOk = checkPullbackEntry(haBullish);
                break;
            case "rsi_pullback":
                entryOk = checkRsiPullbackEntry(data, i, close, haBullish);
                break;
            case "regime":
                entryOk = checkRegimeEntry(data, i, close);
                break;
            case "donchian":
                entryOk = checkDonchianEntry(data, i, close);
                break;
            case "keltner":
                entryOk = checkKeltnerEntry(data, i, close, atr);
                break;
            default: // "flip"
                entryOk = checkFlipEntry(haBullish, haLow, haOpen, close);
                break;
        }

        if (!entryOk) return;

        // ── Common filters ───────────────────────────────────
        if (trendEmaPeriod > 0 && data.has("Trend_EMA")) {
            double tEma = data.get("Trend_EMA")[i];
            if (Double.isNaN(tEma) || close < tEma) return;
            // EMA slope filter: trend EMA must be rising
            if (emaSlopeBars > 0 && i >= emaSlopeBars) {
                double tEmaPrev = data.get("Trend_EMA")[i - emaSlopeBars];
                if (!Double.isNaN(tEmaPrev) && tEma <= tEmaPrev) return;
            }
        }
        if (adxFilter && data.has("ADX")) {
            double adxVal = data.get("ADX")[i];
            if (Double.isNaN(adxVal) || adxVal < adxThreshold) return;
        }
        if (adxRising && data.has("ADX") && i >= adxSlope) {
            double a0 = data.get("ADX")[i], a1 = data.get("ADX")[i - adxSlope];
            if (Double.isNaN(a0) || Double.isNaN(a1) || a0 <= a1) return;
        }
        if (rocMin != 0.0 && data.has("ROC")) {
            double r = data.get("ROC")[i];
            if (Double.isNaN(r) || r < rocMin) return;
        }
        if (volumeFilter && data.has("Vol_SMA") && data.has("Volume")) {
            double vol = data.get("Volume")[i];
            double volSma = data.get("Vol_SMA")[i];
            if (Double.isNaN(volSma) || vol < volMult * volSma) return;
        }
        if (bbFilter && data.has("BB_Width")) {
            double bbw = data.get("BB_Width")[i];
            if (Double.isNaN(bbw) || bbw < bbMinWidth) return;
        }

        // All filters passed — ENTER
        Double sl = slAtrMult > 0 ? close - slAtrMult * atr : null;
        Double tp = tpAtrMult > 0 ? close + tpAtrMult * atr : null;
        if (sl == null || sl < close) {
            engine.entry("Long", Direction.LONG, sl, tp, "HA Entry");
            // entryPrice + highestSinceEntry set on next bar (actual fill) in onBar
        }
    }

    // ── Entry mode: FLIP (classic) ───────────────────────────

    private boolean checkFlipEntry(boolean haBullish, double haLow, double haOpen, double close) {
        if (!haBullish) return false;

        if (confirmBars <= 1) {
            if (prevHaBullish) return false; // already bullish, no flip
        } else {
            if (consecutiveBullish < confirmBars - 1) return false;
        }

        if (strongCandle && haLow < haOpen - 0.001 * close) return false;

        return true;
    }

    // ── Entry mode: PULLBACK ─────────────────────────────────
    // Wait for confirmed uptrend (N bullish bars), then brief pullback (1-M bearish bars),
    // then enter on bullish resumption.

    private boolean checkPullbackEntry(boolean haBullish) {
        if (!haBullish) return false;

        // We need: the peak bullish streak was >= minTrendBars (trend confirmed)
        // AND we just had 1 to maxPullbackBars bearish bars (pullback)
        // AND current bar is bullish (resumption)
        if (peakBullishStreak >= minTrendBars && hadPullback
                && consecutiveBearish >= 1 && consecutiveBearish <= maxPullbackBars
                && !prevHaBullish) {
            return true;
        }

        return false;
    }

    // ── Entry mode: RSI_PULLBACK ─────────────────────────────
    // HA was recently bullish (trend confirmed) + RSI drops below threshold (pullback)
    // Does NOT require current bar to be bullish — we're buying the dip.

    private boolean checkRsiPullbackEntry(StrategyData data, int i, double close, boolean haBullish) {
        if (!data.has("RSI")) return false;
        double rsi = data.get("RSI")[i];
        if (Double.isNaN(rsi)) return false;

        // Trend: HA has been bullish recently
        boolean haTrend = peakBullishStreak >= minTrendBars;
        if (!haTrend) return false;

        // Don't enter if we're in a deep downtrend (too many bearish bars)
        if (consecutiveBearish > maxPullbackBars + 2) return false;

        // Pullback: RSI is below entry threshold
        if (rsi > rsiEntry) return false;

        return true;
    }

    // ── Entry mode: REGIME ────────────────────────────────────
    // HA regime = majority of recent bars were bullish
    // Entry on RSI pullback within bullish HA regime
    // This makes HA a trend FILTER not an entry SIGNAL

    private boolean checkRegimeEntry(StrategyData data, int i, double close) {
        if (!data.has("RSI")) return false;
        double rsi = data.get("RSI")[i];
        if (Double.isNaN(rsi)) return false;

        // Check HA regime: count bullish bars in ring buffer
        int bullishCount = 0;
        for (int v : haHistory) bullishCount += v;
        double bullishPct = (double) bullishCount / haHistory.length;

        if (bullishPct < regimeThreshold) return false;

        // RSI pullback entry
        if (rsi > rsiEntry) return false;

        return true;
    }

    // ── Entry mode: DONCHIAN breakout ────────────────────────
    // Close breaks above the highest high of the prior N bars (Turtle-style).
    private boolean checkDonchianEntry(StrategyData data, int i, double close) {
        if (!data.has("Donch_Hi")) return false;
        double hi = data.get("Donch_Hi")[i];
        return !Double.isNaN(hi) && close > hi;
    }

    // ── Entry mode: KELTNER breakout ─────────────────────────
    // Close breaks above the upper Keltner band (EMA + kcMult*ATR).
    private boolean checkKeltnerEntry(StrategyData data, int i, double close, double atr) {
        if (!data.has("KC_Mid")) return false;
        double mid = data.get("KC_Mid")[i];
        return !Double.isNaN(mid) && close > mid + kcMult * atr;
    }

    // ── State management ─────────────────────────────────────

    private void updateHaState(StrategyData data, int i) {
        double haClose = data.get("HA_Close")[i];
        double haOpen = data.get("HA_Open")[i];
        boolean bullish = haClose > haOpen;

        // Update ring buffer for regime mode
        haHistory[haHistoryIdx % haHistory.length] = bullish ? 1 : 0;
        haHistoryIdx++;

        if (bullish) {
            if (!prevHaBullish) {
                hadPullback = (peakBullishStreak >= minTrendBars && consecutiveBearish >= 1
                        && consecutiveBearish <= maxPullbackBars);
            }
            consecutiveBullish++;
            consecutiveBearish = 0;
            peakBullishStreak = Math.max(peakBullishStreak, consecutiveBullish);
        } else {
            consecutiveBearish++;
            consecutiveBullish = 0;

            if (consecutiveBearish > maxPullbackBars + 2) {
                peakBullishStreak = 0;
                hadPullback = false;
            }
        }
        prevHaBullish = bullish;
    }

    // ── Helpers ──────────────────────────────────────────────
    private static boolean getBool(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }
}
