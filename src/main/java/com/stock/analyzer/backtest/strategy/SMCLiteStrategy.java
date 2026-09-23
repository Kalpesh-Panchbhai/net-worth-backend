package com.stock.analyzer.backtest.strategy;

import com.stock.analyzer.backtest.engine.TradingViewEngine;
import com.stock.analyzer.backtest.indicator.Indicators;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.model.Direction;

import java.util.List;
import java.util.Map;

import static com.stock.analyzer.backtest.strategy.SMCStrategy.*;

/**
 * SMC Lite Strategy — Timeframe: 1D (Daily)
 *
 * Redesigned for high CAGR + low MDD on large stock universes (2100+ stocks).
 *
 * Core insight: Use BOS as a REGIME FILTER, not as the entry trigger.
 * Within bullish regime, enter on frequent pullback recoveries.
 *
 * Architecture:
 * 1. Swing structure → BOS → determines bullish/bearish regime
 * 2. Trend EMA (200) → additional trend filter
 * 3. Entry: pullback recovery — close dips below entry EMA then crosses back above
 *    OR BOS bar entry (first bar of new bullish structure)
 * 4. Exit: bearish BOS (structure break) + exit EMA + trailing stop (layered)
 * 5. SL: ATR-based (tight, predictable)
 */
public class SMCLiteStrategy implements Strategy {

    private String name = "SMC Lite";
    private final String timeframe;
    private String description = "SMC Lite — BOS regime + pullback entry";

    // ── Swing structure ───────────────────────────────────────
    private final int swingLeft;
    private final int swingRight;
    private final boolean requireBos;     // true = need BOS for bullish regime; false = EMA-only
    private final boolean bosExit;         // true = exit on bearish BOS (independent of requireBos)

    // ── Trend filter ──────────────────────────────────────────
    private final int trendEmaPeriod;     // 0 = disabled. Price must be above this EMA.
    private final boolean adxFilter;
    private final double adxThreshold;
    private final int adxPeriod;
    private final boolean stTrendFilter;   // true = only enter when Supertrend dir is bullish
    private final double atrPctMax;       // 0 = disabled. Skip entry if ATR/Close*100 > threshold
    private final double rsiFloor;        // 0 = disabled. Skip entry if RSI < this value (momentum confirm)
    private final boolean stage2Filter;   // Minervini Trend Template: close > EMA50 > EMA150 > EMA200
    private final int emaSlopeBars;       // 0 = disabled. Require trend EMA rising over N bars (Stage 2 confirm)
    private final double maxPctFromHigh;  // 0 = disabled. Skip entry if close < (52w high * (1 - maxPctFromHigh/100))
    private final int highLookback;       // Lookback for "52-week high" (default 252)

    // ── Entry ─────────────────────────────────────────────────
    private final String entryMode;       // "pullback" = EMA pullback recovery
                                          // "rsi" = RSI(2) < threshold pullback
                                          // "ha_flip" = Heikin Ashi bullish flip
                                          // "bos_only" = only on BOS bar
                                          // "any_bullish" = any bar above trend EMA
    private final int entryEmaPeriod;     // Short EMA for pullback detection (default 10)
    private final int rsiPeriod;          // RSI period for rsi mode (default 2)
    private final double rsiThreshold;    // RSI entry threshold (default 55)
    private final boolean bosEntry;       // Also enter on BOS bar itself (default true)
    private final boolean haFlipExit;     // Exit on HA bearish flip (works with any entry mode)
    private final int haExitBars;          // 0=flip exit (default), N=require N consecutive bearish HA bars
    private final boolean stFlipExit;      // Exit on Supertrend bearish flip
    private final int stPeriod;            // Supertrend ATR period (default 10)
    private final double stFactor;         // Supertrend factor (default 3.0)

    // ── SL / TP ────────────────────────────────────────────────
    private final double slAtrMult;       // ATR multiplier for SL (0 = no SL)
    private final double maxLossPct;      // Max loss as % of entry price (0 = disabled, intra-bar via engine SL)
    private final double tpPct;           // Take profit as % gain from entry (0 = disabled, intra-bar via engine TP)
    private final int atrPeriod;

    // ── Trailing stop ─────────────────────────────────────────
    private final boolean trailingStop;
    private final double trailAtrMult;
    private final double trailActivationPct;
    private final double breakevenPct;    // After this % gain, floor trail at entry price (0 = disabled)

    // ── Exit EMA ──────────────────────────────────────────────
    private final int exitEmaPeriod;      // 0 = disabled. Exit when close < this EMA.

    // ── Cooldown ──────────────────────────────────────────────
    private final int cooldownBars;       // min bars between exit and next entry (0 = none)

    // ── State ─────────────────────────────────────────────────
    private int[] lastSh; private double lastShPrice;
    private int[] lastSl; private double lastSlPrice;
    private int structure;                // 1=bullish, -1=bearish, 0=unknown
    private double entryPrice;
    private double trailStop;          // internally-managed trailing stop level
    private boolean trailActive;       // whether trailing has been activated
    private boolean beActive;          // whether breakeven stop is active
    private int lastExitBar;
    private boolean prevBelowEntryEma;    // was close below entry EMA on previous bar?
    private double prevRsi;               // previous bar RSI value
    private boolean prevHaBullish;        // previous bar HA candle was bullish?
    private int consecutiveBearishHa;      // count of consecutive bearish HA bars
    private double prevStDir;              // previous Supertrend direction

    public SMCLiteStrategy(Map<String, Object> params) {
        this.timeframe = getString(params, "timeframe", "1D");
        this.swingLeft = getInt(params, "swing_left", 5);
        this.swingRight = getInt(params, "swing_right", 2);
        this.requireBos = getBool(params, "require_bos", true);
        this.bosExit = getBool(params, "bos_exit", false);

        this.trendEmaPeriod = getInt(params, "trend_ema", 200);
        this.adxFilter = getBool(params, "adx_filter", false);
        this.adxThreshold = getDouble(params, "adx_threshold", 20);
        this.adxPeriod = getInt(params, "adx_period", 14);
        this.stTrendFilter = getBool(params, "st_trend_filter", false);
        this.atrPctMax = getDouble(params, "atr_pct_max", 0.0);
        this.rsiFloor = getDouble(params, "rsi_floor", 0.0);
        this.stage2Filter = getBool(params, "stage2_filter", false);
        this.emaSlopeBars = getInt(params, "ema_slope_bars", 0);
        this.maxPctFromHigh = getDouble(params, "max_pct_from_high", 0.0);
        this.highLookback = getInt(params, "high_lookback", 252);

        this.entryMode = getString(params, "entry_mode", "pullback");
        this.entryEmaPeriod = getInt(params, "entry_ema", 10);
        this.rsiPeriod = getInt(params, "rsi_period", 2);
        this.rsiThreshold = getDouble(params, "rsi_threshold", 55);
        this.bosEntry = getBool(params, "bos_entry", true);
        this.haFlipExit = getBool(params, "ha_flip_exit", false);
        this.haExitBars = getInt(params, "ha_exit_bars", 0);
        this.stFlipExit = getBool(params, "st_flip_exit", false);
        this.stPeriod = getInt(params, "st_period", 10);
        this.stFactor = getDouble(params, "st_factor", 3.0);

        this.slAtrMult = getDouble(params, "sl_atr_mult", 2.0);
        this.maxLossPct = getDouble(params, "max_loss_pct", 0.0);
        this.tpPct = getDouble(params, "tp_pct", 0.0);
        this.atrPeriod = getInt(params, "atr_period", 14);

        this.trailingStop = getBool(params, "trailing_stop", true);
        this.trailAtrMult = getDouble(params, "trail_atr_mult", 1.0);
        this.trailActivationPct = getDouble(params, "trail_activation_pct", 0.0);
        this.breakevenPct = getDouble(params, "breakeven_pct", 0.0);

        this.exitEmaPeriod = getInt(params, "exit_ema", 0);
        this.cooldownBars = getInt(params, "cooldown_bars", 0);

        String vn = getString(params, "variant_name", null);
        if (vn != null) { this.name = vn; this.description = vn; }
        resetState();
    }

    public SMCLiteStrategy() { this(Map.of()); }

    private void resetState() {
        lastSh = null; lastShPrice = 0;
        lastSl = null; lastSlPrice = 0;
        structure = 0;
        entryPrice = 0;
        trailStop = 0; trailActive = false; beActive = false; beActive = false;
        lastExitBar = -999;
        prevBelowEntryEma = false;
        prevRsi = Double.NaN;
        prevHaBullish = false;
        consecutiveBearishHa = 0;
        prevStDir = 0;
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
        if (trendEmaPeriod > 0) data.put("TrendEMA", Indicators.emaAdjustFalse(c, trendEmaPeriod));
        if (entryEmaPeriod > 0 && "pullback".equals(entryMode))
            data.put("EntryEMA", Indicators.emaAdjustFalse(c, entryEmaPeriod));
        if ("rsi".equals(entryMode) || rsiFloor > 0) data.put("RSI", Indicators.rsi(c, rsiPeriod));
        if ("ha_flip".equals(entryMode) || haFlipExit) {
            double[][] ha = Indicators.heikinAshi(data.open(), h, l, c);
            data.put("HA_Open", ha[0]);
            data.put("HA_Close", ha[3]);
        }
        if ("st_flip".equals(entryMode) || stFlipExit || stTrendFilter) {
            double[][] st = Indicators.supertrend(h, l, c, stPeriod, stFactor);
            data.put("ST_Value", st[0]);
            data.put("ST_Dir", st[1]);
            data.put("ST_Flip", st[2]);
        }
        if (exitEmaPeriod > 0) data.put("ExitEMA", Indicators.emaAdjustFalse(c, exitEmaPeriod));
        if (adxFilter) data.put("ADX", Indicators.adx(h, l, c, adxPeriod));
        // Minervini Trend Template indicators
        if (stage2Filter) {
            data.put("EMA50", Indicators.emaAdjustFalse(c, 50));
            data.put("EMA150", Indicators.emaAdjustFalse(c, 150));
            data.put("EMA200", Indicators.emaAdjustFalse(c, 200));
        }
        if (maxPctFromHigh > 0) {
            int n = c.length;
            double[] rollingHigh = new double[n];
            for (int i = 0; i < n; i++) {
                int start = Math.max(0, i - highLookback + 1);
                double maxH = h[start];
                for (int j = start + 1; j <= i; j++) if (h[j] > maxH) maxH = h[j];
                rollingHigh[i] = maxH;
            }
            data.put("RollingHigh", rollingHigh);
        }
        return data;
    }

    @Override
    public void onBar(TradingViewEngine engine, StrategyData data, int i, boolean tradeActive) {
        int minBars = Math.max(swingLeft + swingRight + 1, Math.max(trendEmaPeriod, entryEmaPeriod) + 1);
        if (i < minBars) return;

        double[] high = data.high(), low = data.low(), close = data.close();
        double atr = data.get("ATR")[i];
        if (Double.isNaN(atr) || atr <= 0) return;

        // Position state transitions: capture next-bar-open fill, reset on exit
        boolean hasPosition = engine.getPosition() != null;
        if (!hasPosition && entryPrice != 0) {
            entryPrice = 0;
        }
        if (hasPosition && entryPrice == 0) {
            entryPrice = engine.getPosition().getEntryPrice();
        }

        // ── 1. Swing point detection & BOS ────────────────────
        boolean bosBar = false;
        if (requireBos || bosExit) {
            int pivot = i - swingRight;
            if (detectSwingHigh(high, pivot)) { lastSh = new int[]{pivot}; lastShPrice = high[pivot]; }
            if (detectSwingLow(low, pivot)) { lastSl = new int[]{pivot}; lastSlPrice = low[pivot]; }

            if (lastSh != null && close[i] > lastShPrice && structure != 1) {
                structure = 1;
                bosBar = true;
            }
            if (lastSl != null && close[i] < lastSlPrice && structure != -1) {
                structure = -1;
                if (engine.getPosition() != null) {
                    engine.closeAll("Bearish BOS");
                    lastExitBar = i;
                    trailStop = 0; trailActive = false; beActive = false;
                }
            }
        }

        // ── 3. Trailing stop (internally managed, like HeikinAshiStrategy) ──
        if (trailingStop && engine.getPosition() != null && entryPrice > 0) {
            double profitPct = (close[i] - entryPrice) / entryPrice * 100;

            // Breakeven stop: after breakevenPct gain, floor trail at entry price
            if (breakevenPct > 0 && !beActive && profitPct >= breakevenPct) {
                beActive = true;
                if (!trailActive || entryPrice > trailStop) {
                    trailStop = entryPrice;
                    trailActive = true;
                }
            }

            if (profitPct >= trailActivationPct) {
                double newTrail = high[i] - trailAtrMult * atr;
                // Ensure trail never goes below breakeven floor when BE is active
                if (beActive && newTrail < entryPrice) newTrail = entryPrice;
                if (!trailActive || newTrail > trailStop) {
                    trailStop = newTrail;
                    trailActive = true;
                }
            }
            if (trailActive && close[i] < trailStop) {
                engine.closeAll("Trail Stop");
                lastExitBar = i;
                trailStop = 0; trailActive = false; beActive = false; beActive = false;
            }
        }

        // ── 3b. HA flip exit ─────────────────────────────────
        if (haFlipExit && engine.getPosition() != null && data.has("HA_Close")) {
            double hc = data.get("HA_Close")[i], ho = data.get("HA_Open")[i];
            boolean haBullish = hc > ho;
            if (haExitBars > 0) {
                // Multi-bar: require N consecutive bearish HA bars
                if (!haBullish) {
                    consecutiveBearishHa++;
                    if (consecutiveBearishHa >= haExitBars) {
                        engine.closeAll("HA Bearish x" + haExitBars);
                        lastExitBar = i;
                        consecutiveBearishHa = 0;
                        trailStop = 0; trailActive = false; beActive = false;
                    }
                } else {
                    consecutiveBearishHa = 0;
                }
            } else {
                // Simple flip: previous bullish → current bearish
                if (!haBullish && prevHaBullish) {
                    engine.closeAll("HA Bearish");
                    lastExitBar = i;
                    trailStop = 0; trailActive = false; beActive = false;
                }
            }
        }

        // ── 3c. Supertrend flip exit ────────────────────────
        if (stFlipExit && engine.getPosition() != null && data.has("ST_Flip")) {
            double stFlip = data.get("ST_Flip")[i];
            if (stFlip == -1) {
                engine.closeAll("ST Bearish");
                lastExitBar = i;
                trailStop = 0; trailActive = false; beActive = false;
            }
        }

        // ── 4. Exit EMA ──────────────────────────────────────
        if (exitEmaPeriod > 0 && engine.getPosition() != null) {
            double exitEma = data.get("ExitEMA")[i];
            if (!Double.isNaN(exitEma) && close[i] < exitEma) {
                engine.closeAll("Exit EMA");
                lastExitBar = i;
            }
        }

        // ── 5. Track SL exits ────────────────────────────────
        if (engine.getPosition() == null && lastExitBar < i) {
            var trades = engine.getClosedTrades();
            if (!trades.isEmpty()) {
                String ec = trades.get(trades.size() - 1).getExitComment();
                if ("Stop Loss".equals(ec)) lastExitBar = i;
            }
        }

        // ── 6. Track pullback state ──────────────────────────
        boolean belowEntryEma = false;
        if ("pullback".equals(entryMode) && entryEmaPeriod > 0) {
            double entryEma = data.get("EntryEMA")[i];
            if (!Double.isNaN(entryEma)) {
                belowEntryEma = close[i] < entryEma;
            }
        }

        double curRsi = Double.NaN;
        if ("rsi".equals(entryMode)) {
            curRsi = data.get("RSI")[i];
        }

        boolean haBullish = false;
        if (data.has("HA_Close")) {
            double hc = data.get("HA_Close")[i], ho = data.get("HA_Open")[i];
            haBullish = hc > ho;
        }

        if (!tradeActive) {
            prevBelowEntryEma = belowEntryEma;
            prevRsi = curRsi;
            prevHaBullish = haBullish;
            return;
        }

        // ── 7. Entry logic ───────────────────────────────────
        if (engine.getPosition() != null) {
            prevBelowEntryEma = belowEntryEma;
            prevRsi = curRsi;
            prevHaBullish = haBullish;
            return;
        }

        // Must be in bullish structure (if BOS required)
        if (requireBos && structure != 1) {
            prevBelowEntryEma = belowEntryEma;
            prevRsi = curRsi;
            prevHaBullish = haBullish;
            return;
        }

        // Cooldown after exit
        if (cooldownBars > 0 && (i - lastExitBar) < cooldownBars) {
            prevBelowEntryEma = belowEntryEma;
            prevRsi = curRsi;
            prevHaBullish = haBullish;
            return;
        }

        // Trend EMA filter
        if (trendEmaPeriod > 0) {
            double ema = data.get("TrendEMA")[i];
            if (Double.isNaN(ema) || close[i] < ema) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
            // EMA slope filter: require trend EMA rising over emaSlopeBars
            if (emaSlopeBars > 0 && i >= emaSlopeBars) {
                double emaPrev = data.get("TrendEMA")[i - emaSlopeBars];
                if (Double.isNaN(emaPrev) || ema <= emaPrev) {
                    prevBelowEntryEma = belowEntryEma;
                    prevRsi = curRsi;
                    prevHaBullish = haBullish;
                    return;
                }
            }
        }

        // Stage 2 filter (Minervini Trend Template): close > EMA50 > EMA150 > EMA200
        if (stage2Filter) {
            double e50 = data.get("EMA50")[i];
            double e150 = data.get("EMA150")[i];
            double e200 = data.get("EMA200")[i];
            if (Double.isNaN(e50) || Double.isNaN(e150) || Double.isNaN(e200)
                || close[i] <= e50 || e50 <= e150 || e150 <= e200) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
        }

        // 52-week high proximity filter: skip if too far below recent high
        if (maxPctFromHigh > 0 && data.has("RollingHigh")) {
            double rh = data.get("RollingHigh")[i];
            if (!Double.isNaN(rh) && rh > 0) {
                double pctFromHigh = (rh - close[i]) / rh * 100.0;
                if (pctFromHigh > maxPctFromHigh) {
                    prevBelowEntryEma = belowEntryEma;
                    prevRsi = curRsi;
                    prevHaBullish = haBullish;
                    return;
                }
            }
        }

        // ADX filter
        if (adxFilter) {
            double adx = data.get("ADX")[i];
            if (Double.isNaN(adx) || adx < adxThreshold) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
        }

        // Supertrend trend filter
        if (stTrendFilter && data.has("ST_Dir")) {
            double stDir = data.get("ST_Dir")[i];
            if (Double.isNaN(stDir) || stDir != 1) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
        }

        // Volatility filter: skip if ATR% of close exceeds threshold
        if (atrPctMax > 0) {
            double atrPct = atr / close[i] * 100;
            if (atrPct > atrPctMax) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
        }

        // RSI floor filter: skip if RSI below minimum (momentum confirm)
        if (rsiFloor > 0 && data.has("RSI")) {
            double rsi = data.get("RSI")[i];
            if (Double.isNaN(rsi) || rsi < rsiFloor) {
                prevBelowEntryEma = belowEntryEma;
                prevRsi = curRsi;
                prevHaBullish = haBullish;
                return;
            }
        }

        boolean enter = false;
        String comment = "";

        // BOS bar entry
        if (bosEntry && bosBar) {
            enter = true;
            comment = "BOS Entry";
        }

        // Pullback recovery entry (EMA crossover)
        if (!enter && "pullback".equals(entryMode)) {
            if (prevBelowEntryEma && !belowEntryEma) {
                enter = true;
                comment = "Pullback Entry";
            }
        }

        // RSI pullback entry: previous RSI was below threshold, current above
        if (!enter && "rsi".equals(entryMode)) {
            if (!Double.isNaN(prevRsi) && !Double.isNaN(curRsi)
                    && prevRsi < rsiThreshold && curRsi >= rsiThreshold) {
                enter = true;
                comment = "RSI Pullback";
            }
        }

        // HA flip entry: previous bar was bearish, current is bullish
        if (!enter && "ha_flip".equals(entryMode)) {
            if (!prevHaBullish && haBullish) {
                enter = true;
                comment = "HA Flip Entry";
            }
        }

        // Supertrend flip entry: bullish flip (direction changes to 1)
        if (!enter && "st_flip".equals(entryMode) && data.has("ST_Flip")) {
            double stFlip = data.get("ST_Flip")[i];
            if (stFlip == 1) {
                enter = true;
                comment = "ST Flip Entry";
            }
        }

        // Any bullish bar entry (most aggressive)
        if (!enter && "any_bullish".equals(entryMode)) {
            enter = true;
            comment = "Bullish Entry";
        }

        prevBelowEntryEma = belowEntryEma;
        prevRsi = curRsi;
        prevHaBullish = haBullish;

        if (enter) {
            // Determine SL: prefer max_loss_pct (percentage-based), fallback to ATR-based
            Double slVal = null;
            if (maxLossPct > 0) {
                slVal = close[i] * (1.0 - maxLossPct / 100.0);
            } else if (slAtrMult > 0) {
                double sl = close[i] - slAtrMult * atr;
                if (sl >= close[i]) return;
                slVal = sl;
            }
            // Take profit: percentage-based (intra-bar via engine)
            Double tpVal = tpPct > 0 ? close[i] * (1.0 + tpPct / 100.0) : null;
            engine.entry("Long", Direction.LONG, slVal, tpVal, comment);
            // entryPrice set on next bar (actual fill) at top of onBar
            trailStop = 0; trailActive = false; beActive = false;
        }
    }

    // ── Swing detection ───────────────────────────────────────

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
}
