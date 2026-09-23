package com.stock.analyzer.backtest.config;

import com.stock.analyzer.backtest.strategy.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * Registry of the TOP 50 strategies ranked on 2170 stocks (1D, 2018-now).
 * Score = 50% Calmar + 50% Robustness.
 *
 * Helper methods smcl() and ha() are kept for creating new variants.
 * All strategy classes (SMCLiteStrategy, HeikinAshiStrategy, EnhancedSMCStrategy,
 * SMCv2Strategy, SMCStrategy, QuantStrategy) remain unchanged for reuse.
 */
public final class VariantRegistry {

    private VariantRegistry() {}

    // Captures every variant's parameter map + strategy class as they are built
    // (populated by smcl/ha/quant). Lets the refinement optimizer seed a local
    // search from the exact params of any registry winner without name-parsing.
    public static final Map<String, Map<String, Object>> VARIANT_PARAMS = new HashMap<>();
    public static final Map<String, String> VARIANT_CLASS = new HashMap<>();

    /** Build a strategy supplier from a class name + param map (for refined variants). */
    public static Supplier<Strategy> build(String className, Map<String, Object> params) {
        switch (className == null ? "" : className) {
            case "SMCLiteStrategy":     return () -> new SMCLiteStrategy(params);
            case "QuantStrategy":       return () -> new QuantStrategy(params);
            case "HeikinAshiStrategy":
            default:                    return () -> new HeikinAshiStrategy(params);
        }
    }

    public static LinkedHashMap<String, Supplier<Strategy>> buildAll() {
        LinkedHashMap<String, Supplier<Strategy>> s = new LinkedHashMap<>();
        // R41 — Fourth-pass: beat every deployed winner
        addAllStockR41(s);
        // R40 — Third-pass: untested combo pairs (WIDE+TP, CD+WIDE, REG+TP, etc.)
        addAllStockR40(s);
        // R39 — Second-pass sector optimization: combos that R38 missed
        addAllStockR39(s);
        // R38 — Deep sector-targeted optimization: 350+ new combos
        addAllStockR38(s);
        // R37 — MDD-reduction overlay on winning sector patterns
        addAllStockR37(s);
        // R36 — Final push: momentum + ADX combinations + sector cross-pollination
        addAllStockR36(s);
        // R35 — Robustness-focused: ultra-selective filters for cross-asset consistency
        addAllStockR35(s);
        // R34 — Sector-tuned fine sweeps targeting 99+ score
        addAllStockR34(s);
        // R33 — Triple-Crown variants (high Sharpe + high Calmar + high Robust)
        addAllStockR33(s);
        // R32 — Minervini SEPA / quality-filtered swing (reduce false trades)
        addAllStockR32(s);
        // R31 — improvements to SMCL_R4:BOSX_SW20_3 (cut MDD, boost CAGR)
        addAllStockR31(s);
        // R30 — research-validated swing trading strategies for Indian market
        addAllStockR30(s);
        // R29 — risk-controlled variants targeting score 90+
        addAllStockR29(s);
        // All R28 variants (final optimization round)
        addAllStockR28(s);
        // Best configs from earlier rounds for comparison
        addAllStockR23(s);
        addAllStockR24(s);
        addAllStockR25(s);
        addAllStockR26(s);
        addAllStockR27(s);
        // Earlier all-stock rounds
        addAllStockR22(s);
        addAllStockR21(s);
        addAllStockR20(s);
        addAllStockR19(s);
        addAllStockR18(s);
        addAllStockR17(s);
        addAllStockR16(s);
        addAllStockR15c(s);
        addAllStockR15b(s);
        addAllStockR15(s);
        addAllStockR14(s);
        addAllStockR13(s);
        addAllStockR12(s);
        addAllStockR11(s);
        addAllStockR10(s);
        addAllStockR9(s);
        addAllStockR8(s);
        addAllStockR7(s);
        addAllStockR6(s);
        addAllStockR5(s);
        addAllStockR4(s);
        addAllStockR3(s);
        addAllStockR2(s);
        addAllStockSweep(s);
        // Top50 + experimental rounds
        addTop50(s);
        addBestWinners(s);
        addR9Experimental(s);
        addR16FineTune(s);
        addR17Push(s);
        addR18Push(s);
        addR19Push(s);
        addR20Push(s);
        addR21Push(s);
        addR22Push(s);
        addR23Push(s);
        addR24Push(s);
        addR25Push(s);
        addR26Push(s);
        addR27Push(s);
        addR28Push(s);
        addR29Push(s);
        addR30Push(s);
        addR31Push(s);
        addR32Push(s);
        return s;
    }

    // ── Helpers (kept for creating new variants) ───────────────────

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    // ══════════════════════════════════════════════════════════════
    //  TOP 50 STRATEGIES (ranked on 2170 stocks, 1D, 2018-now)
    //  Score = 50% Calmar + 50% Robustness
    // ══════════════════════════════════════════════════════════════

    private static void addTop50(Map<String, Supplier<Strategy>> s) {

        // ── SMC Lite base: HA flip entry/exit + 0.5 ATR trailing @ 3% ──
        Map<String, Object> smclBase = new HashMap<>();
        smclBase.put("require_bos", false);
        smclBase.put("trend_ema", 0);
        smclBase.put("entry_mode", "ha_flip");
        smclBase.put("bos_entry", false);
        smclBase.put("ha_flip_exit", true);
        smclBase.put("sl_atr_mult", 0.0);
        smclBase.put("trailing_stop", true);
        smclBase.put("trail_atr_mult", 0.5);
        smclBase.put("trail_activation_pct", 3.0);

        // #8  SMCL_R3:HA_NOEMA — pure HA flip, no filters (Score 50.9)
        smcl(s, "SMCL_R3", "HA_NOEMA", smclBase);

        // R5 variants — trail/activation/ATR/SL/ADX sweeps on HA flip base
        smcl(s, "SMCL_R5", "ATR20", smclBase, "atr_period", 20);                                           // #2
        smcl(s, "SMCL_R5", "ATR7", smclBase, "atr_period", 7);                                             // #16
        smcl(s, "SMCL_R5", "TR0.4", smclBase, "trail_atr_mult", 0.4);                                     // #27
        smcl(s, "SMCL_R5", "TR0.45", smclBase, "trail_atr_mult", 0.45);                                   // #7
        smcl(s, "SMCL_R5", "TR0.55", smclBase, "trail_atr_mult", 0.55);                                   // #20
        smcl(s, "SMCL_R5", "TR0.6", smclBase, "trail_atr_mult", 0.6);                                     // #29
        smcl(s, "SMCL_R5", "A1", smclBase, "trail_activation_pct", 1.0);                                   // #28
        smcl(s, "SMCL_R5", "A2", smclBase, "trail_activation_pct", 2.0);                                   // #15
        smcl(s, "SMCL_R5", "A4", smclBase, "trail_activation_pct", 4.0);                                   // #21
        smcl(s, "SMCL_R5", "A5", smclBase, "trail_activation_pct", 5.0);                                   // #34
        smcl(s, "SMCL_R5", "TR0.45_A2", smclBase, "trail_atr_mult", 0.45, "trail_activation_pct", 2.0);   // #25
        smcl(s, "SMCL_R5", "TR0.55_A4", smclBase, "trail_atr_mult", 0.55, "trail_activation_pct", 4.0);   // #33
        smcl(s, "SMCL_R5", "SL4", smclBase, "sl_atr_mult", 4.0);                                          // #40
        smcl(s, "SMCL_R5", "SL5", smclBase, "sl_atr_mult", 5.0);                                          // #35
        smcl(s, "SMCL_R5", "ADX10", smclBase, "adx_filter", true, "adx_threshold", 10.0);                  // #41
        smcl(s, "SMCL_R5", "TR0.5_A4_SL4", smclBase, "trail_activation_pct", 4.0, "sl_atr_mult", 4.0);    // #43
        smcl(s, "SMCL_R5", "BOSX_TR0.5", smclBase, "bos_exit", true);                                     // #11

        // R4 variants — BOS exit-only + swing param sweeps
        Map<String, Object> r4Base = new HashMap<>(smclBase);
        r4Base.put("bos_exit", true);
        smcl(s, "SMCL_R4", "BOSX_SW3_1", r4Base, "swing_left", 3, "swing_right", 1);                      // #19
        smcl(s, "SMCL_R4", "BOSX_SW3_2", r4Base, "swing_left", 3, "swing_right", 2);                      // #18
        smcl(s, "SMCL_R4", "BOSX_SW5_1", r4Base, "swing_left", 5, "swing_right", 1);                      // #14
        smcl(s, "SMCL_R4", "BOSX_SW5_2", r4Base);                                                          // #10
        smcl(s, "SMCL_R4", "BOSX_SW10_2", r4Base, "swing_left", 10, "swing_right", 2);                    // #13
        smcl(s, "SMCL_R4", "BOSX_SW10_3", r4Base, "swing_left", 10, "swing_right", 3);                    // #9
        smcl(s, "SMCL_R4", "BOSX_SW15_3", r4Base, "swing_left", 15, "swing_right", 3);                    // #12
        smcl(s, "SMCL_R4", "BOSX_SW20_3", r4Base, "swing_left", 20, "swing_right", 3);                    // #6
        smcl(s, "SMCL_R4", "BOSX_TR0.3", r4Base, "trail_atr_mult", 0.3);                                  // #42
        smcl(s, "SMCL_R4", "BOSX_TR0.7", r4Base, "trail_atr_mult", 0.7);                                  // #47

        // R6 variants — ATR20 base + trail/activation combos
        Map<String, Object> r6Base = new HashMap<>(smclBase);
        r6Base.put("atr_period", 20);
        smcl(s, "SMCL_R6", "ATR20_TR0.45", r6Base, "trail_atr_mult", 0.45);                                // #5
        smcl(s, "SMCL_R6", "ATR20_A4", r6Base, "trail_activation_pct", 4.0);                                // #17
        smcl(s, "SMCL_R6", "ATR20_A2_TR0.45", r6Base, "trail_activation_pct", 2.0, "trail_atr_mult", 0.45);// #22
        smcl(s, "SMCL_R6", "ATR20_A4_TR0.45", r6Base, "trail_activation_pct", 4.0, "trail_atr_mult", 0.45);// #23
        smcl(s, "SMCL_R6", "ATR20_A4_TR0.55", r6Base, "trail_activation_pct", 4.0, "trail_atr_mult", 0.55);// #26

        // R8 variants — Dual exit (HA flip + Supertrend flip) + trailing
        Map<String, Object> dualBase = new HashMap<>(smclBase);
        dualBase.put("st_flip_exit", true);
        dualBase.put("atr_period", 20);
        smcl(s, "SMCL_R8", "HA_DUAL_7_3", dualBase, "st_period", 7);                                       // #1
        smcl(s, "SMCL_R8", "HA_DUAL_10_3", dualBase);                                                       // #3
        smcl(s, "SMCL_R8", "HA_DUAL_10_2", dualBase, "st_factor", 2.0);                                     // #4

        // ── Heikin Ashi Enhanced: HA flip entry/exit + tight trailing (16 variants) ──
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);

        // R10
        ha(s, "HA_R10", "TR0.5_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 1.0);   // #38
        ha(s, "HA_R10", "TR0.7_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.7, "trail_activation_pct", 1.0);   // #48
        ha(s, "HA_R10", "TR0.7_A2", haBase, "trailing_stop", true, "trail_atr_mult", 0.7, "trail_activation_pct", 2.0);   // #50

        // R11
        ha(s, "HA_R11", "TR0.4_A2", haBase, "trailing_stop", true, "trail_atr_mult", 0.4, "trail_activation_pct", 2.0);   // #49
        ha(s, "HA_R11", "TR0.45_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.45, "trail_activation_pct", 1.0); // #44
        ha(s, "HA_R11", "TR0.5_A0.5", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 0.5); // #45
        ha(s, "HA_R11", "TR0.5_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 1.0);   // #39
        ha(s, "HA_R11", "TR0.5_A1.5", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 1.5); // #32
        ha(s, "HA_R11", "TR0.5_A1_ATR7", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 1.0, "atr_period", 7);  // #46
        ha(s, "HA_R11", "TR0.5_A1_ATR20", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 1.0, "atr_period", 20);// #30
        ha(s, "HA_R11", "TR0.5_A2", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 2.0);   // #31
        ha(s, "HA_R11", "TR0.5_A3", haBase, "trailing_stop", true, "trail_atr_mult", 0.5, "trail_activation_pct", 3.0);   // #24
        ha(s, "HA_R11", "TR0.55_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.55, "trail_activation_pct", 1.0); // #37
        ha(s, "HA_R11", "TR0.6_A1", haBase, "trailing_stop", true, "trail_atr_mult", 0.6, "trail_activation_pct", 1.0);   // #36
    }

    // ══════════════════════════════════════════════════════════════
    //  R9 EXPERIMENTAL — Targeting Score 60+
    //  Key insight: All existing top variants have trend_ema=0.
    //  Adding trend EMA filter should:
    //    1. ↑ pctProfitable (skip downtrending stocks)
    //    2. ↑ consistency (reduce cross-asset variance)
    //    3. ↓ MaxDD (no entries in bear markets)
    //  Also exploring: exit EMA, cooldown, multi-bar HA exit,
    //  Supertrend trend filter, and pullback/regime entries.
    // ══════════════════════════════════════════════════════════════

    private static void addR9Experimental(Map<String, Supplier<Strategy>> s) {

        // ── GROUP A: SMC Lite + Trend EMA filter (biggest gap) ────
        // Base: HA flip entry/exit + trailing + trend EMA 200
        Map<String, Object> r9Base = new HashMap<>();
        r9Base.put("require_bos", false);
        r9Base.put("entry_mode", "ha_flip");
        r9Base.put("bos_entry", false);
        r9Base.put("ha_flip_exit", true);
        r9Base.put("sl_atr_mult", 0.0);
        r9Base.put("trailing_stop", true);
        r9Base.put("trail_atr_mult", 0.5);
        r9Base.put("trail_activation_pct", 3.0);
        r9Base.put("trend_ema", 200);  // THE KEY CHANGE

        // A1: Pure trend EMA 200 + HA flip (baseline for R9)
        smcl(s, "SMCL_R9", "EMA200", r9Base);
        // A2: Trend EMA 150 (faster, more signals)
        smcl(s, "SMCL_R9", "EMA150", r9Base, "trend_ema", 150);
        // A3: Trend EMA 100
        smcl(s, "SMCL_R9", "EMA100", r9Base, "trend_ema", 100);
        // A4: EMA200 + ATR20 (best ATR from R5)
        smcl(s, "SMCL_R9", "EMA200_ATR20", r9Base, "atr_period", 20);
        // A5: EMA200 + tighter trailing (0.45)
        smcl(s, "SMCL_R9", "EMA200_TR0.45", r9Base, "trail_atr_mult", 0.45);
        // A6: EMA200 + wider trailing (0.6) for more room
        smcl(s, "SMCL_R9", "EMA200_TR0.6", r9Base, "trail_atr_mult", 0.6);
        // A7: EMA200 + lower activation (2%) — trail kicks in sooner
        smcl(s, "SMCL_R9", "EMA200_A2", r9Base, "trail_activation_pct", 2.0);
        // A8: EMA200 + higher activation (5%) — let winners run more
        smcl(s, "SMCL_R9", "EMA200_A5", r9Base, "trail_activation_pct", 5.0);

        // ── GROUP B: Trend EMA + Dual Exit (HA flip + Supertrend) ──
        Map<String, Object> dualEmaBase = new HashMap<>(r9Base);
        dualEmaBase.put("st_flip_exit", true);
        dualEmaBase.put("atr_period", 20);

        // B1: EMA200 + dual exit (best combo from R8)
        smcl(s, "SMCL_R9", "EMA200_DUAL7", dualEmaBase, "st_period", 7);
        // B2: EMA200 + dual exit + ST period 10
        smcl(s, "SMCL_R9", "EMA200_DUAL10", dualEmaBase);
        // B3: EMA200 + dual exit + ST factor 2.0 (tighter ST)
        smcl(s, "SMCL_R9", "EMA200_DUAL10_F2", dualEmaBase, "st_factor", 2.0);
        // B4: EMA150 + dual exit
        smcl(s, "SMCL_R9", "EMA150_DUAL7", dualEmaBase, "trend_ema", 150, "st_period", 7);

        // ── GROUP C: Trend EMA + Exit EMA (faster loss-cutting) ────
        // Exit when price drops below short EMA — reduces MaxDD
        smcl(s, "SMCL_R9", "EMA200_XEMA50", r9Base, "exit_ema", 50);
        smcl(s, "SMCL_R9", "EMA200_XEMA100", r9Base, "exit_ema", 100);
        smcl(s, "SMCL_R9", "EMA150_XEMA50", r9Base, "trend_ema", 150, "exit_ema", 50);

        // ── GROUP D: Trend EMA + BOS exit (structure-based exit) ───
        smcl(s, "SMCL_R9", "EMA200_BOSX", r9Base, "bos_exit", true);
        smcl(s, "SMCL_R9", "EMA200_BOSX_SW20", r9Base, "bos_exit", true,
                "swing_left", 20, "swing_right", 3);
        smcl(s, "SMCL_R9", "EMA150_BOSX", r9Base, "trend_ema", 150, "bos_exit", true);

        // ── GROUP E: Trend EMA + Cooldown (avoid revenge trading) ──
        smcl(s, "SMCL_R9", "EMA200_CD5", r9Base, "cooldown_bars", 5);
        smcl(s, "SMCL_R9", "EMA200_CD10", r9Base, "cooldown_bars", 10);

        // ── GROUP F: Trend EMA + Multi-bar HA exit (reduce whipsaw) ─
        smcl(s, "SMCL_R9", "EMA200_HAX2", r9Base, "ha_exit_bars", 2);
        smcl(s, "SMCL_R9", "EMA200_HAX3", r9Base, "ha_exit_bars", 3);

        // ── GROUP G: Trend EMA + Supertrend trend filter ───────────
        // Require BOTH price > EMA200 AND Supertrend bullish
        smcl(s, "SMCL_R9", "EMA200_STF", r9Base, "st_trend_filter", true);
        smcl(s, "SMCL_R9", "EMA200_STF_7", r9Base, "st_trend_filter", true,
                "st_period", 7, "st_factor", 3.0);
        smcl(s, "SMCL_R9", "EMA150_STF", r9Base, "trend_ema", 150, "st_trend_filter", true);

        // ── GROUP H: Trend EMA + ADX filter ────────────────────────
        smcl(s, "SMCL_R9", "EMA200_ADX15", r9Base, "adx_filter", true, "adx_threshold", 15.0);
        smcl(s, "SMCL_R9", "EMA200_ADX20", r9Base, "adx_filter", true, "adx_threshold", 20.0);

        // ── GROUP I: HA Enhanced + Trend EMA (new territory) ───────
        Map<String, Object> haEmaBase = new HashMap<>();
        haEmaBase.put("entry_mode", "flip");
        haEmaBase.put("ha_flip_exit", true);
        haEmaBase.put("sl_atr_mult", 0.0);
        haEmaBase.put("tp_atr_mult", 0.0);
        haEmaBase.put("trend_ema", 200);
        haEmaBase.put("trailing_stop", true);

        // I1: HA flip + EMA200 + wide trail (like top HA_R1 but with EMA filter)
        ha(s, "HA_R12", "EMA200_TR5_A3", haEmaBase,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        // I2: HA flip + EMA200 + medium trail
        ha(s, "HA_R12", "EMA200_TR3_A2", haEmaBase,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        // I3: HA flip + EMA200 + tight trail
        ha(s, "HA_R12", "EMA200_TR0.5_A3", haEmaBase,
                "trail_atr_mult", 0.5, "trail_activation_pct", 3.0);
        // I4: HA flip + EMA150
        ha(s, "HA_R12", "EMA150_TR5_A3", haEmaBase,
                "trend_ema", 150, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        // I5: HA flip + EMA200 + exit EMA 50
        ha(s, "HA_R12", "EMA200_XEMA50_TR5_A3", haEmaBase,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── GROUP J: HA Pullback + Trend EMA (higher WR entry) ─────
        Map<String, Object> haPbBase = new HashMap<>();
        haPbBase.put("entry_mode", "pullback");
        haPbBase.put("min_trend_bars", 5);
        haPbBase.put("max_pullback_bars", 2);
        haPbBase.put("ha_flip_exit", true);
        haPbBase.put("sl_atr_mult", 0.0);
        haPbBase.put("tp_atr_mult", 0.0);
        haPbBase.put("trend_ema", 200);
        haPbBase.put("trailing_stop", true);

        ha(s, "HA_R12", "PB_EMA200_TR5_A3", haPbBase,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R12", "PB_EMA200_TR3_A2", haPbBase,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R12", "PB_EMA200_TR0.5_A3", haPbBase,
                "trail_atr_mult", 0.5, "trail_activation_pct", 3.0);
        ha(s, "HA_R12", "PB_EMA150_TR5_A3", haPbBase,
                "trend_ema", 150, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── GROUP K: HA Regime + Trend EMA (trend filter as entry) ──
        Map<String, Object> haRegBase = new HashMap<>();
        haRegBase.put("entry_mode", "regime");
        haRegBase.put("regime_window", 10);
        haRegBase.put("regime_threshold", 0.6);
        haRegBase.put("rsi_entry", 55.0);
        haRegBase.put("rsi_period", 2);
        haRegBase.put("ha_flip_exit", true);
        haRegBase.put("sl_atr_mult", 0.0);
        haRegBase.put("tp_atr_mult", 0.0);
        haRegBase.put("trend_ema", 200);
        haRegBase.put("trailing_stop", true);

        ha(s, "HA_R12", "REG_EMA200_TR5_A3", haRegBase,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R12", "REG_EMA200_TR3_A2", haRegBase,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R12", "REG_EMA150_TR5_A3", haRegBase,
                "trend_ema", 150, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── GROUP L: Best combos — kitchen sink (layered filters) ──
        // EMA200 + dual exit + ADX + BOS exit (maximum filtering)
        Map<String, Object> maxFilter = new HashMap<>(r9Base);
        maxFilter.put("st_flip_exit", true);
        maxFilter.put("st_period", 7);
        maxFilter.put("bos_exit", true);
        maxFilter.put("atr_period", 20);
        smcl(s, "SMCL_R9", "MAX_EMA200_DUAL_BOSX", maxFilter);

        // EMA200 + dual exit + cooldown 5
        Map<String, Object> dualCd = new HashMap<>(dualEmaBase);
        dualCd.put("st_period", 7);
        smcl(s, "SMCL_R9", "EMA200_DUAL7_CD5", dualCd, "cooldown_bars", 5);

        // EMA200 + trail 0.45 + activation 2% + ATR20 (tight optimized)
        smcl(s, "SMCL_R9", "EMA200_ATR20_TR0.45_A2", r9Base,
                "atr_period", 20, "trail_atr_mult", 0.45, "trail_activation_pct", 2.0);

        // ══════════════════════════════════════════════════════════
        //  GROUP M-S: HIGH-CAGR strategies (wide trailing)
        //  Key insight: Old HA_R1:TRAIL5_ACT3 had CAGR 14.65%
        //  because trail_atr_mult=5.0 let winners run.
        //  Combine wide trails with moderate loss-cutting for 60+.
        // ══════════════════════════════════════════════════════════

        // ── GROUP M: HA wide trail (reclaim old HA_R1 territory) ──
        Map<String, Object> haWide = new HashMap<>();
        haWide.put("entry_mode", "flip");
        haWide.put("ha_flip_exit", true);
        haWide.put("sl_atr_mult", 0.0);
        haWide.put("tp_atr_mult", 0.0);
        haWide.put("trailing_stop", true);

        // M1-M6: Pure HA flip + wide trail sweeps (like old HA_R1)
        ha(s, "HA_R13", "TR5_A3", haWide, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "TR5_A2", haWide, "trail_atr_mult", 5.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R13", "TR5_A5", haWide, "trail_atr_mult", 5.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R13", "TR4_A3", haWide, "trail_atr_mult", 4.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "TR3_A2", haWide, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R13", "TR3_A5", haWide, "trail_atr_mult", 3.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R13", "TR6_A3", haWide, "trail_atr_mult", 6.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "TR7_A5", haWide, "trail_atr_mult", 7.0, "trail_activation_pct", 5.0);

        // M7: Strong candle filter (no lower wick = strong entry)
        ha(s, "HA_R13", "S_TR5_A3", haWide, "strong_candle", true,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "S_TR3_A2", haWide, "strong_candle", true,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);

        // ── GROUP N: Wide trail + Exit EMA (cap big drawdowns) ────
        // Wide trail lets winners run, exit EMA cuts losers early
        ha(s, "HA_R13", "TR5_A3_XEMA50", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_ema", 50);
        ha(s, "HA_R13", "TR5_A3_XEMA100", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_ema", 100);
        ha(s, "HA_R13", "TR3_A2_XEMA50", haWide,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0, "exit_ema", 50);
        ha(s, "HA_R13", "TR5_A3_XEMA75", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_ema", 75);

        // ── GROUP O: Wide trail + light EMA filter (50/100) ───────
        // EMA50/100 is less restrictive than EMA200
        ha(s, "HA_R13", "EMA50_TR5_A3", haWide, "trend_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "EMA100_TR5_A3", haWide, "trend_ema", 100,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "EMA50_TR3_A2", haWide, "trend_ema", 50,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R13", "EMA100_TR3_A2", haWide, "trend_ema", 100,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);

        // ── GROUP P: Wide trail + multi-bar HA exit ───────────────
        // Require 2-3 consecutive bearish HA bars before exit
        // Reduces whipsaw exits → higher CAGR + lower MaxDD
        ha(s, "HA_R13", "TR5_A3_XB2", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_bearish_bars", 2);
        ha(s, "HA_R13", "TR5_A3_XB3", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_bearish_bars", 3);
        ha(s, "HA_R13", "TR3_A2_XB2", haWide,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0, "exit_bearish_bars", 2);
        ha(s, "HA_R13", "TR5_A5_XB2", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 5.0, "exit_bearish_bars", 2);

        // ── GROUP Q: Wide trail + cooldown after losses ───────────
        ha(s, "HA_R13", "TR5_A3_CD5", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "cooldown_bars", 5);
        ha(s, "HA_R13", "TR5_A3_CD10", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "cooldown_bars", 10);
        ha(s, "HA_R13", "TR5_A3_CD3_L2", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0,
                "cooldown_bars", 5, "max_consec_losses", 2);

        // ── GROUP R: Wide trail + confirm bars (entry quality) ────
        // Require 2+ consecutive bullish HA bars before entering
        ha(s, "HA_R13", "TR5_A3_CB2", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "confirm_bars", 2);
        ha(s, "HA_R13", "TR5_A3_CB3", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "confirm_bars", 3);

        // ── GROUP S: SMC Lite with wide trail (hybrid) ────────────
        // HA flip entry (like SMC Lite base) but with wide trail
        Map<String, Object> smclWide = new HashMap<>();
        smclWide.put("require_bos", false);
        smclWide.put("entry_mode", "ha_flip");
        smclWide.put("bos_entry", false);
        smclWide.put("ha_flip_exit", true);
        smclWide.put("sl_atr_mult", 0.0);
        smclWide.put("trailing_stop", true);
        smclWide.put("trend_ema", 0);

        smcl(s, "SMCL_R9", "WIDE_TR5_A3", smclWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        smcl(s, "SMCL_R9", "WIDE_TR3_A2", smclWide,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        smcl(s, "SMCL_R9", "WIDE_TR5_A3_DUAL7", smclWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0,
                "st_flip_exit", true, "st_period", 7, "atr_period", 20);
        smcl(s, "SMCL_R9", "WIDE_TR5_A3_BOSX", smclWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "bos_exit", true);

        // ── GROUP T: Combo — wide trail + EMA50 + exit EMA ────────
        ha(s, "HA_R13", "EMA50_TR5_A3_XEMA100", haWide, "trend_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_ema", 100);
        ha(s, "HA_R13", "EMA100_TR5_A3_XB2", haWide, "trend_ema", 100,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "exit_bearish_bars", 2);
        ha(s, "HA_R13", "TR5_A3_XEMA50_XB2", haWide,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0,
                "exit_ema", 50, "exit_bearish_bars", 2);

        // ── GROUP U: EMA slope filter (trend must be rising) ──────
        ha(s, "HA_R13", "EMA50_SLP5_TR5_A3", haWide, "trend_ema", 50,
                "ema_slope_bars", 5, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R13", "EMA100_SLP10_TR5_A3", haWide, "trend_ema", 100,
                "ema_slope_bars", 10, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ══════════════════════════════════════════════════════════
        //  GROUP V-Z: NO HA FLIP EXIT — let trailing stop work!
        //  Key insight: HA flip exit fires on single bearish bar,
        //  overriding the trailing stop and capping CAGR.
        //  Disabling it lets winners run, trailing stop protects.
        // ══════════════════════════════════════════════════════════

        // ── GROUP V: Trail-only exit (no HA flip, no exit EMA) ────
        Map<String, Object> trailOnly = new HashMap<>();
        trailOnly.put("entry_mode", "flip");
        trailOnly.put("ha_flip_exit", false);       // KEY: disabled!
        trailOnly.put("sl_atr_mult", 0.0);
        trailOnly.put("tp_atr_mult", 0.0);
        trailOnly.put("trailing_stop", true);

        // V1-V6: Pure trail exit, sweep multiplier + activation
        ha(s, "HA_R14", "TONLY_TR5_A3", trailOnly, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "TONLY_TR3_A2", trailOnly, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "TONLY_TR3_A5", trailOnly, "trail_atr_mult", 3.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R14", "TONLY_TR4_A3", trailOnly, "trail_atr_mult", 4.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "TONLY_TR5_A5", trailOnly, "trail_atr_mult", 5.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R14", "TONLY_TR7_A5", trailOnly, "trail_atr_mult", 7.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R14", "TONLY_TR2_A1", trailOnly, "trail_atr_mult", 2.0, "trail_activation_pct", 1.0);
        ha(s, "HA_R14", "TONLY_TR2_A3", trailOnly, "trail_atr_mult", 2.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "TONLY_TR1.5_A2", trailOnly, "trail_atr_mult", 1.5, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "TONLY_TR1_A1", trailOnly, "trail_atr_mult", 1.0, "trail_activation_pct", 1.0);

        // ── GROUP W: Trail-only + trend EMA filter ────────────────
        ha(s, "HA_R14", "TONLY_EMA50_TR5_A3", trailOnly, "trend_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "TONLY_EMA100_TR5_A3", trailOnly, "trend_ema", 100,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "TONLY_EMA50_TR3_A2", trailOnly, "trend_ema", 50,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);

        // ── GROUP X: Multi-bar HA exit ONLY (no single-bar flip) ──
        Map<String, Object> multiBar = new HashMap<>();
        multiBar.put("entry_mode", "flip");
        multiBar.put("ha_flip_exit", false);         // no single-bar flip
        multiBar.put("exit_bearish_bars", 3);        // require 3 consecutive bearish
        multiBar.put("sl_atr_mult", 0.0);
        multiBar.put("tp_atr_mult", 0.0);
        multiBar.put("trailing_stop", true);

        ha(s, "HA_R14", "XB3_TR5_A3", multiBar,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XB3_TR3_A2", multiBar,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "XB2_TR5_A3", multiBar, "exit_bearish_bars", 2,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XB2_TR3_A2", multiBar, "exit_bearish_bars", 2,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "XB4_TR5_A3", multiBar, "exit_bearish_bars", 4,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── GROUP Y: Trail + exit EMA ONLY (no HA exit at all) ────
        Map<String, Object> trailEma = new HashMap<>();
        trailEma.put("entry_mode", "flip");
        trailEma.put("ha_flip_exit", false);
        trailEma.put("sl_atr_mult", 0.0);
        trailEma.put("tp_atr_mult", 0.0);
        trailEma.put("trailing_stop", true);

        ha(s, "HA_R14", "XEMA50_TR5_A3", trailEma,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XEMA100_TR5_A3", trailEma,
                "exit_ema", 100, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XEMA50_TR3_A2", trailEma,
                "exit_ema", 50, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "XEMA75_TR5_A3", trailEma,
                "exit_ema", 75, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XEMA50_TR5_A5", trailEma,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 5.0);

        // ── GROUP Z: Multi-bar HA + exit EMA + trail (triple layer) ─
        Map<String, Object> tripleExit = new HashMap<>();
        tripleExit.put("entry_mode", "flip");
        tripleExit.put("ha_flip_exit", false);
        tripleExit.put("sl_atr_mult", 0.0);
        tripleExit.put("tp_atr_mult", 0.0);
        tripleExit.put("trailing_stop", true);

        ha(s, "HA_R14", "XB3_XEMA50_TR5_A3", tripleExit, "exit_bearish_bars", 3,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XB2_XEMA50_TR5_A3", tripleExit, "exit_bearish_bars", 2,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "XB3_XEMA100_TR5_A3", tripleExit, "exit_bearish_bars", 3,
                "exit_ema", 100, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── GROUP Z2: Pullback entry + trail-only exit ────────────
        Map<String, Object> pbTrail = new HashMap<>();
        pbTrail.put("entry_mode", "pullback");
        pbTrail.put("min_trend_bars", 3);
        pbTrail.put("max_pullback_bars", 2);
        pbTrail.put("ha_flip_exit", false);
        pbTrail.put("sl_atr_mult", 0.0);
        pbTrail.put("tp_atr_mult", 0.0);
        pbTrail.put("trailing_stop", true);

        ha(s, "HA_R14", "PB_TONLY_TR5_A3", pbTrail,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R14", "PB_TONLY_TR3_A2", pbTrail,
                "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R14", "PB_XB3_TR5_A3", pbTrail, "exit_bearish_bars", 3,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ══════════════════════════════════════════════════════════
        //  R15: ULTRA-LOW MDD — targeting MaxDD < -30%
        //  Approach: trail-only exit (no HA flip) + hard SL +
        //  trend filter + exit EMA + tighter trailing
        // ══════════════════════════════════════════════════════════

        // ── BASE: No HA flip exit + SL protection ─────────────────
        Map<String, Object> lowMddBase = new HashMap<>();
        lowMddBase.put("entry_mode", "flip");
        lowMddBase.put("ha_flip_exit", false);
        lowMddBase.put("tp_atr_mult", 0.0);
        lowMddBase.put("trailing_stop", true);

        // ── A: Hard SL + wide trail (cap single-trade losses) ─────
        // SL prevents catastrophic single-position losses
        ha(s, "HA_R15", "SL2_TR5_A3", lowMddBase,
                "sl_atr_mult", 2.0, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_TR5_A3", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL4_TR5_A3", lowMddBase,
                "sl_atr_mult", 4.0, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL5_TR5_A3", lowMddBase,
                "sl_atr_mult", 5.0, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_TR4_A3", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 4.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_TR3_A2", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R15", "SL2_TR3_A2", lowMddBase,
                "sl_atr_mult", 2.0, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
        ha(s, "HA_R15", "SL4_TR4_A3", lowMddBase,
                "sl_atr_mult", 4.0, "trail_atr_mult", 4.0, "trail_activation_pct", 3.0);

        // ── B: Hard SL + trend EMA + wide trail ───────────────────
        // Trend filter avoids entering downtrends; SL caps losses
        ha(s, "HA_R15", "SL3_EMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_EMA100_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 100, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL2_EMA50_TR5_A3", lowMddBase, "sl_atr_mult", 2.0,
                "trend_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL4_EMA50_TR5_A3", lowMddBase, "sl_atr_mult", 4.0,
                "trend_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_EMA50_TR4_A3", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 4.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_EMA50_TR3_A2", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);

        // ── C: Hard SL + exit EMA + wide trail ────────────────────
        // Exit EMA as extra safety net: exit if price drops below
        ha(s, "HA_R15", "SL3_XEMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_XEMA100_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "exit_ema", 100, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL4_XEMA50_TR5_A3", lowMddBase, "sl_atr_mult", 4.0,
                "exit_ema", 50, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_XEMA75_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "exit_ema", 75, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── D: Triple safety — SL + trend EMA + exit EMA + trail ──
        ha(s, "HA_R15", "SL3_EMA50_XEMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "exit_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_EMA100_XEMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 100, "exit_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL4_EMA50_XEMA50_TR5_A3", lowMddBase, "sl_atr_mult", 4.0,
                "trend_ema", 50, "exit_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── E: Hard SL + multi-bar HA exit + trail ────────────────
        // Multi-bar exit is lenient (3+ bearish bars needed) → good CAGR
        // SL caps the worst case
        ha(s, "HA_R15", "SL3_XB3_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "exit_bearish_bars", 3, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL4_XB3_TR5_A3", lowMddBase, "sl_atr_mult", 4.0,
                "exit_bearish_bars", 3, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_XB2_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "exit_bearish_bars", 2, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_XB3_TR3_A2", lowMddBase, "sl_atr_mult", 3.0,
                "exit_bearish_bars", 3, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);

        // ── F: Tighter trailing (activate sooner, trail closer) ───
        // Lower activation = trail activates with less profit
        // Lower multiplier = trail is closer to price → locks profit faster
        ha(s, "HA_R15", "SL3_TR2_A0", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 2.0, "trail_activation_pct", 0.0);
        ha(s, "HA_R15", "SL3_TR1.5_A0", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 1.5, "trail_activation_pct", 0.0);
        ha(s, "HA_R15", "SL3_TR2_A1", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 2.0, "trail_activation_pct", 1.0);
        ha(s, "HA_R15", "SL4_TR2_A0", lowMddBase,
                "sl_atr_mult", 4.0, "trail_atr_mult", 2.0, "trail_activation_pct", 0.0);
        ha(s, "HA_R15", "SL3_TR3_A0", lowMddBase,
                "sl_atr_mult", 3.0, "trail_atr_mult", 3.0, "trail_activation_pct", 0.0);

        // ── G: SL + tighter trail + trend EMA ─────────────────────
        ha(s, "HA_R15", "SL3_EMA50_TR2_A0", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 2.0, "trail_activation_pct", 0.0);
        ha(s, "HA_R15", "SL3_EMA50_TR2_A1", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 2.0, "trail_activation_pct", 1.0);
        ha(s, "HA_R15", "SL3_EMA50_TR3_A0", lowMddBase, "sl_atr_mult", 3.0,
                "trend_ema", 50, "trail_atr_mult", 3.0, "trail_activation_pct", 0.0);
        ha(s, "HA_R15", "SL4_EMA50_TR2_A0", lowMddBase, "sl_atr_mult", 4.0,
                "trend_ema", 50, "trail_atr_mult", 2.0, "trail_activation_pct", 0.0);

        // ── H: Cooldown after losses + SL + trail ─────────────────
        ha(s, "HA_R15", "SL3_TR5_A3_CD5", lowMddBase, "sl_atr_mult", 3.0,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "cooldown_bars", 5);
        ha(s, "HA_R15", "SL3_TR5_A3_CD10", lowMddBase, "sl_atr_mult", 3.0,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0, "cooldown_bars", 10);
        ha(s, "HA_R15", "SL3_TR5_A3_CD5_L2", lowMddBase, "sl_atr_mult", 3.0,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0,
                "cooldown_bars", 10, "max_consec_losses", 2);

        // ── I: Confirm bars + SL + trail (higher quality entries) ──
        ha(s, "HA_R15", "SL3_CB2_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "confirm_bars", 2, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_CB3_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "confirm_bars", 3, "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_CB2_EMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "confirm_bars", 2, "trend_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);

        // ── J: ADX + SL + trail (only trend in strong markets) ────
        ha(s, "HA_R15", "SL3_ADX15_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "adx_filter", true, "adx_threshold", 15.0,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_ADX20_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "adx_filter", true, "adx_threshold", 20.0,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
        ha(s, "HA_R15", "SL3_ADX15_EMA50_TR5_A3", lowMddBase, "sl_atr_mult", 3.0,
                "adx_filter", true, "adx_threshold", 15.0, "trend_ema", 50,
                "trail_atr_mult", 5.0, "trail_activation_pct", 3.0);
    }

    // ── Reusable helper: register SMCLiteStrategy variant ─────────

    private static void smcl(Map<String, Supplier<Strategy>> s,
                             String round, String variant,
                             Map<String, Object> base, Object... overrides) {
        String fullName = round + ":" + variant;
        Map<String, Object> params = new HashMap<>(base);
        for (int i = 0; i < overrides.length; i += 2) params.put((String) overrides[i], overrides[i + 1]);
        params.put("variant_name", fullName);
        VARIANT_PARAMS.put(fullName, new HashMap<>(params));
        VARIANT_CLASS.put(fullName, "SMCLiteStrategy");
        s.put(fullName, () -> new SMCLiteStrategy(params));
    }

    // ── Reusable helper: register HeikinAshiStrategy variant ──────

    private static void ha(Map<String, Supplier<Strategy>> s, String prefix, String name,
                           Map<String, Object> base, Object... overrides) {
        String fullName = prefix + ":" + name;
        Map<String, Object> params = new HashMap<>(base);
        for (int i = 0; i < overrides.length; i += 2) params.put((String) overrides[i], overrides[i + 1]);
        params.put("variant_name", fullName);
        VARIANT_PARAMS.put(fullName, new HashMap<>(params));
        VARIANT_CLASS.put(fullName, "HeikinAshiStrategy");
        s.put(fullName, () -> new HeikinAshiStrategy(params));
    }

    private static void quant(Map<String, Supplier<Strategy>> s, String prefix, String name,
                              Map<String, Object> base, Object... overrides) {
        String fullName = prefix + ":" + name;
        Map<String, Object> params = new HashMap<>(base);
        for (int i = 0; i < overrides.length; i += 2) params.put((String) overrides[i], overrides[i + 1]);
        params.put("variant_name", fullName);
        VARIANT_PARAMS.put(fullName, new HashMap<>(params));
        VARIANT_CLASS.put(fullName, "QuantStrategy");
        s.put(fullName, () -> new QuantStrategy(params));
    }

    // ══════════════════════════════════════════════════════════════
    //  R16: FINE-TUNE around SL3_CB2_TR5_A3 (Score 69.7)
    //  Sweep every parameter individually to find 70+
    // ══════════════════════════════════════════════════════════════
    private static void addR16FineTune(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        // Updated winners: SL4, TR6, A3
        base.put("sl_atr_mult", 4.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);

        // Baseline (should reproduce ~71.0)
        ha(s, "HA_R16", "BASE", base);

        // ── DENSE GRID: SL × TR (fine-grained around winners) ─────
        double[] sls = {3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0};
        double[] trs = {5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0};
        for (double sl : sls) {
            for (double tr : trs) {
                String name = String.format("SL%.1f_TR%.1f", sl, tr);
                ha(s, "HA_R16", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── ACTIVATION sweep with best SL/TR combos ──────────────
        double[] acts = {0.0, 1.0, 2.0, 4.0, 5.0, 7.0};
        double[][] bestPairs = {{4.0, 6.0}, {5.0, 6.0}, {6.0, 5.0}, {6.0, 6.0}, {5.0, 7.0}, {4.5, 6.5}};
        for (double[] pair : bestPairs) {
            for (double a : acts) {
                String name = String.format("SL%.1f_TR%.1f_A%.0f", pair[0], pair[1], a);
                ha(s, "HA_R16", name, base,
                        "sl_atr_mult", pair[0], "trail_atr_mult", pair[1], "trail_activation_pct", a);
            }
        }

        // ── ATR period sweep with best combos ─────────────────────
        int[] atrs = {7, 10, 20};
        for (int atr : atrs) {
            ha(s, "HA_R16", "SL4_TR6_ATR" + atr, base,
                    "sl_atr_mult", 4.0, "trail_atr_mult", 6.0, "atr_period", atr);
            ha(s, "HA_R16", "SL6_TR5_ATR" + atr, base,
                    "sl_atr_mult", 6.0, "trail_atr_mult", 5.0, "atr_period", atr);
            ha(s, "HA_R16", "SL6_TR6_ATR" + atr, base,
                    "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "atr_period", atr);
        }

        // ── Cooldown with best combos ─────────────────────────────
        ha(s, "HA_R16", "SL4_TR6_CD5", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0, "cooldown_bars", 5);
        ha(s, "HA_R16", "SL4_TR6_CD10", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0, "cooldown_bars", 10);
        ha(s, "HA_R16", "SL6_TR5_CD5", base, "sl_atr_mult", 6.0, "trail_atr_mult", 5.0, "cooldown_bars", 5);
        ha(s, "HA_R16", "SL6_TR6_CD5", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "cooldown_bars", 5);
        ha(s, "HA_R16", "SL6_TR6_CD10", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "cooldown_bars", 10);

        // ── ADX with best combos ──────────────────────────────────
        ha(s, "HA_R16", "SL4_TR6_ADX15", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0,
                "adx_filter", true, "adx_threshold", 15.0);
        ha(s, "HA_R16", "SL4_TR6_ADX20", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0,
                "adx_filter", true, "adx_threshold", 20.0);
        ha(s, "HA_R16", "SL6_TR6_ADX15", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0,
                "adx_filter", true, "adx_threshold", 15.0);
        ha(s, "HA_R16", "SL6_TR5_ADX20", base, "sl_atr_mult", 6.0, "trail_atr_mult", 5.0,
                "adx_filter", true, "adx_threshold", 20.0);

        // ── Confirm bar variation with best combos ────────────────
        ha(s, "HA_R16", "SL4_TR6_CB1", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0, "confirm_bars", 1);
        ha(s, "HA_R16", "SL4_TR6_CB3", base, "sl_atr_mult", 4.0, "trail_atr_mult", 6.0, "confirm_bars", 3);
        ha(s, "HA_R16", "SL6_TR5_CB1", base, "sl_atr_mult", 6.0, "trail_atr_mult", 5.0, "confirm_bars", 1);
        ha(s, "HA_R16", "SL6_TR6_CB1", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "confirm_bars", 1);
        ha(s, "HA_R16", "SL6_TR6_CB3", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "confirm_bars", 3);
    }

    // ══════════════════════════════════════════════════════════════
    //  R17: PUSH PAST 72.8 — ultra-fine grid around SL5.5_TR6.0
    //  + combine best modifiers (CD10, ATR10, ADX, confirm bars)
    // ══════════════════════════════════════════════════════════════
    private static void addR17Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 5.5);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);

        // Baseline (should reproduce 72.8)
        ha(s, "HA_R17", "BASE", base);

        // ── ULTRA-FINE GRID: SL × TR (0.25 steps around winner) ───
        double[] sls = {4.75, 5.0, 5.25, 5.5, 5.75, 6.0, 6.25};
        double[] trs = {5.25, 5.5, 5.75, 6.0, 6.25, 6.5, 6.75};
        for (double sl : sls) {
            for (double tr : trs) {
                String name = String.format("SL%.2f_TR%.2f", sl, tr);
                ha(s, "HA_R17", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── ACTIVATION sweep with SL5.5_TR6.0 ────────────────────
        double[] acts = {0.0, 1.0, 2.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0};
        for (double a : acts) {
            ha(s, "HA_R17", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── BEST MODIFIERS on SL5.5_TR6.0 ────────────────────────
        // Cooldown (CD10 scored 72.4 with SL6_TR6)
        ha(s, "HA_R17", "CD5", base, "cooldown_bars", 5);
        ha(s, "HA_R17", "CD10", base, "cooldown_bars", 10);
        ha(s, "HA_R17", "CD15", base, "cooldown_bars", 15);
        ha(s, "HA_R17", "CD7", base, "cooldown_bars", 7);

        // ATR periods (ATR10 helped before)
        ha(s, "HA_R17", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R17", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R17", "ATR20", base, "atr_period", 20);

        // Confirm bars variation
        ha(s, "HA_R17", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R17", "CB3", base, "confirm_bars", 3);
        ha(s, "HA_R17", "CB4", base, "confirm_bars", 4);

        // ADX filter
        ha(s, "HA_R17", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R17", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);
        ha(s, "HA_R17", "ADX20", base, "adx_filter", true, "adx_threshold", 20.0);

        // Strong candle filter
        ha(s, "HA_R17", "STRONG", base, "strong_candle", true);

        // Multi-bar bearish exit as additional safety
        ha(s, "HA_R17", "XB3", base, "exit_bearish_bars", 3);
        ha(s, "HA_R17", "XB4", base, "exit_bearish_bars", 4);
        ha(s, "HA_R17", "XB5", base, "exit_bearish_bars", 5);

        // ── COMBOS: stack best modifiers ──────────────────────────
        // CD10 + ATR10
        ha(s, "HA_R17", "CD10_ATR10", base, "cooldown_bars", 10, "atr_period", 10);
        // CD10 + ADX15
        ha(s, "HA_R17", "CD10_ADX15", base, "cooldown_bars", 10,
                "adx_filter", true, "adx_threshold", 15.0);
        // CD10 + CB3
        ha(s, "HA_R17", "CD10_CB3", base, "cooldown_bars", 10, "confirm_bars", 3);
        // ATR10 + ADX15
        ha(s, "HA_R17", "ATR10_ADX15", base, "atr_period", 10,
                "adx_filter", true, "adx_threshold", 15.0);
        // ATR10 + CB3
        ha(s, "HA_R17", "ATR10_CB3", base, "atr_period", 10, "confirm_bars", 3);
        // Strong + CD10
        ha(s, "HA_R17", "STRONG_CD10", base, "strong_candle", true, "cooldown_bars", 10);
        // Strong + ATR10
        ha(s, "HA_R17", "STRONG_ATR10", base, "strong_candle", true, "atr_period", 10);

        // ── ACTIVATION fine-tune with nearby SL/TR combos ─────────
        double[][] nearPairs = {{5.25, 6.0}, {5.5, 5.75}, {5.5, 6.25}, {5.75, 6.0}, {5.0, 6.5}};
        double[] fineActs = {2.0, 4.0, 5.0};
        for (double[] pair : nearPairs) {
            for (double a : fineActs) {
                String name = String.format("SL%.2f_TR%.2f_A%.0f", pair[0], pair[1], a);
                ha(s, "HA_R17", name, base,
                        "sl_atr_mult", pair[0], "trail_atr_mult", pair[1], "trail_activation_pct", a);
            }
        }

        // ── COOLDOWN sweep on top nearby SL/TR combos ─────────────
        ha(s, "HA_R17", "SL5.25_TR6.0_CD10", base, "sl_atr_mult", 5.25, "trail_atr_mult", 6.0, "cooldown_bars", 10);
        ha(s, "HA_R17", "SL5.75_TR6.0_CD10", base, "sl_atr_mult", 5.75, "trail_atr_mult", 6.0, "cooldown_bars", 10);
        ha(s, "HA_R17", "SL5.5_TR6.25_CD10", base, "sl_atr_mult", 5.5, "trail_atr_mult", 6.25, "cooldown_bars", 10);
        ha(s, "HA_R17", "SL5.5_TR5.75_CD10", base, "sl_atr_mult", 5.5, "trail_atr_mult", 5.75, "cooldown_bars", 10);

        // ── Max consec losses (stop after N losses in a row) ──────
        ha(s, "HA_R17", "CD10_L2", base, "cooldown_bars", 10, "max_consec_losses", 2);
        ha(s, "HA_R17", "CD10_L3", base, "cooldown_bars", 10, "max_consec_losses", 3);
        ha(s, "HA_R17", "CD5_L2", base, "cooldown_bars", 5, "max_consec_losses", 2);
    }

    // ══════════════════════════════════════════════════════════════
    //  R18: PUSH PAST 73.8 — stack modifiers around CD10_L3 winner
    //  Base: SL5.5_TR6.0_CB2 + CD10 + max_consec_losses=3
    // ══════════════════════════════════════════════════════════════
    private static void addR18Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 5.5);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);
        base.put("cooldown_bars", 10);
        base.put("max_consec_losses", 3);

        // Baseline (should reproduce 73.8)
        ha(s, "HA_R18", "BASE", base);

        // ── SL/TR fine-tune around SL5.5_TR6.0 with CD10_L3 ──────
        double[] sls = {4.5, 5.0, 5.25, 5.5, 5.75, 6.0, 6.5, 7.0};
        double[] trs = {5.0, 5.5, 5.75, 6.0, 6.25, 6.5, 7.0};
        for (double sl : sls) {
            for (double tr : trs) {
                String name = String.format("SL%.1f_TR%.1f", sl, tr);
                ha(s, "HA_R18", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── Activation sweep on CD10_L3 base ──────────────────────
        double[] acts = {0.0, 1.0, 2.0, 4.0, 5.0, 7.0};
        for (double a : acts) {
            ha(s, "HA_R18", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── Cooldown fine-tune ────────────────────────────────────
        int[] cds = {7, 8, 12, 15, 20};
        for (int cd : cds) {
            ha(s, "HA_R18", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── Max consec losses sweep ───────────────────────────────
        ha(s, "HA_R18", "L1", base, "max_consec_losses", 1);
        ha(s, "HA_R18", "L2", base, "max_consec_losses", 2);
        ha(s, "HA_R18", "L4", base, "max_consec_losses", 4);
        ha(s, "HA_R18", "L5", base, "max_consec_losses", 5);

        // ── ATR period on CD10_L3 ─────────────────────────────────
        ha(s, "HA_R18", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R18", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R18", "ATR20", base, "atr_period", 20);

        // ── Confirm bars variation ────────────────────────────────
        ha(s, "HA_R18", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R18", "CB3", base, "confirm_bars", 3);
        ha(s, "HA_R18", "CB4", base, "confirm_bars", 4);

        // ── ADX filter on CD10_L3 ─────────────────────────────────
        ha(s, "HA_R18", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R18", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);
        ha(s, "HA_R18", "ADX20", base, "adx_filter", true, "adx_threshold", 20.0);

        // ── Strong candle on CD10_L3 ──────────────────────────────
        ha(s, "HA_R18", "STRONG", base, "strong_candle", true);

        // ── Combos: ATR + other mods ──────────────────────────────
        ha(s, "HA_R18", "ATR10_CB3", base, "atr_period", 10, "confirm_bars", 3);
        ha(s, "HA_R18", "ATR10_ADX15", base, "atr_period", 10,
                "adx_filter", true, "adx_threshold", 15.0);
        ha(s, "HA_R18", "ATR10_A4", base, "atr_period", 10, "trail_activation_pct", 4.0);
        ha(s, "HA_R18", "ATR10_STRONG", base, "atr_period", 10, "strong_candle", true);
        ha(s, "HA_R18", "ATR10_L2", base, "atr_period", 10, "max_consec_losses", 2);

        // ── Best SL/TR combos + ATR10 ─────────────────────────────
        ha(s, "HA_R18", "SL5.0_TR6.0_ATR10", base, "sl_atr_mult", 5.0, "trail_atr_mult", 6.0, "atr_period", 10);
        ha(s, "HA_R18", "SL6.0_TR6.0_ATR10", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "atr_period", 10);
        ha(s, "HA_R18", "SL5.5_TR6.5_ATR10", base, "sl_atr_mult", 5.5, "trail_atr_mult", 6.5, "atr_period", 10);
        ha(s, "HA_R18", "SL6.0_TR5.5_ATR10", base, "sl_atr_mult", 6.0, "trail_atr_mult", 5.5, "atr_period", 10);

        // ── Best SL/TR combos + activation tweaks ─────────────────
        ha(s, "HA_R18", "SL5.0_TR6.0_A4", base, "sl_atr_mult", 5.0, "trail_atr_mult", 6.0, "trail_activation_pct", 4.0);
        ha(s, "HA_R18", "SL6.0_TR6.0_A4", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "trail_activation_pct", 4.0);
        ha(s, "HA_R18", "SL5.0_TR6.0_A5", base, "sl_atr_mult", 5.0, "trail_atr_mult", 6.0, "trail_activation_pct", 5.0);
        ha(s, "HA_R18", "SL6.0_TR6.0_A5", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "trail_activation_pct", 5.0);

        // ── CD fine-tune combos ───────────────────────────────────
        ha(s, "HA_R18", "CD12_L3", base, "cooldown_bars", 12);
        ha(s, "HA_R18", "CD15_L3", base, "cooldown_bars", 15);
        ha(s, "HA_R18", "CD8_L3", base, "cooldown_bars", 8);
        ha(s, "HA_R18", "CD20_L3", base, "cooldown_bars", 20);
    }

    // ══════════════════════════════════════════════════════════════
    //  R19: PUSH PAST 74.6 — longer cooldowns + combos
    //  Base: SL5.5_TR6.0_CB2 + CD15 + L3 (Score 74.6)
    // ══════════════════════════════════════════════════════════════
    private static void addR19Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 5.5);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);
        base.put("cooldown_bars", 15);
        base.put("max_consec_losses", 3);

        // Baseline (should reproduce 74.6)
        ha(s, "HA_R19", "BASE", base);

        // ── COOLDOWN extremes ─────────────────────────────────────
        for (int cd : new int[]{10, 12, 15, 18, 20, 25, 30, 40, 50}) {
            ha(s, "HA_R19", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── SL/TR grid with CD15 ─────────────────────────────────
        double[] sls = {4.5, 5.0, 5.25, 5.5, 5.75, 6.0, 6.5, 7.0, 8.0};
        double[] trs = {5.0, 5.5, 6.0, 6.5, 7.0, 8.0};
        for (double sl : sls) {
            for (double tr : trs) {
                String name = String.format("SL%.1f_TR%.1f", sl, tr);
                ha(s, "HA_R19", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── Activation sweep with CD15 ────────────────────────────
        for (double a : new double[]{0.0, 1.0, 2.0, 4.0, 5.0, 7.0, 10.0}) {
            ha(s, "HA_R19", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── ATR period with CD15 ─────────────────────────────────
        ha(s, "HA_R19", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R19", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R19", "ATR20", base, "atr_period", 20);

        // ── Confirm bars ──────────────────────────────────────────
        ha(s, "HA_R19", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R19", "CB3", base, "confirm_bars", 3);

        // ── Max consec losses ─────────────────────────────────────
        ha(s, "HA_R19", "L1", base, "max_consec_losses", 1);
        ha(s, "HA_R19", "L2", base, "max_consec_losses", 2);
        ha(s, "HA_R19", "L4", base, "max_consec_losses", 4);
        ha(s, "HA_R19", "L5", base, "max_consec_losses", 5);
        ha(s, "HA_R19", "L0", base, "max_consec_losses", 0);  // no limit

        // ── ADX ───────────────────────────────────────────────────
        ha(s, "HA_R19", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R19", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);

        // ── Strong candle ─────────────────────────────────────────
        ha(s, "HA_R19", "STRONG", base, "strong_candle", true);

        // ── Combos: ATR10 + various ───────────────────────────────
        ha(s, "HA_R19", "ATR10_A4", base, "atr_period", 10, "trail_activation_pct", 4.0);
        ha(s, "HA_R19", "ATR10_CB3", base, "atr_period", 10, "confirm_bars", 3);
        ha(s, "HA_R19", "ATR10_ADX15", base, "atr_period", 10,
                "adx_filter", true, "adx_threshold", 15.0);

        // ── CD sweep + A4 (activation 4 helped in R18) ────────────
        for (int cd : new int[]{12, 15, 18, 20, 25}) {
            ha(s, "HA_R19", "CD" + cd + "_A4", base,
                    "cooldown_bars", cd, "trail_activation_pct", 4.0);
        }

        // ── Best SL/TR + longer cooldowns ─────────────────────────
        ha(s, "HA_R19", "SL6.0_TR6.0_CD20", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "cooldown_bars", 20);
        ha(s, "HA_R19", "SL6.0_TR6.0_CD25", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "cooldown_bars", 25);
        ha(s, "HA_R19", "SL5.0_TR6.0_CD20", base, "sl_atr_mult", 5.0, "trail_atr_mult", 6.0, "cooldown_bars", 20);
        ha(s, "HA_R19", "SL6.0_TR6.5_CD20", base, "sl_atr_mult", 6.0, "trail_atr_mult", 6.5, "cooldown_bars", 20);

        // ── Strong + ATR10 combos ─────────────────────────────────
        ha(s, "HA_R19", "STRONG_ATR10", base, "strong_candle", true, "atr_period", 10);
        ha(s, "HA_R19", "STRONG_A4", base, "strong_candle", true, "trail_activation_pct", 4.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R20: CREATIVE PUSH — different entries, exit combos,
    //  and wider parameter ranges to break the 74.6 ceiling
    // ══════════════════════════════════════════════════════════════
    private static void addR20Push(Map<String, Supplier<Strategy>> s) {

        // ── Winner base: SL5.5_TR6.0_CB2_CD15_L3 ─────────────────
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 5.5);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);
        base.put("cooldown_bars", 15);
        base.put("max_consec_losses", 3);

        ha(s, "HA_R20", "BASE", base);

        // ── A: Exit bearish bars + CD15 (multi-bar exit as safety) ─
        for (int xb : new int[]{2, 3, 4, 5, 6}) {
            ha(s, "HA_R20", "XB" + xb, base, "exit_bearish_bars", xb);
        }

        // ── B: Trend EMA break exit + CD15 ────────────────────────
        for (int ema : new int[]{20, 30, 50, 75, 100}) {
            ha(s, "HA_R20", "XEMA" + ema, base, "exit_ema", ema);
        }

        // ── C: Pullback entry + CD15 base (higher quality entries) ─
        Map<String, Object> pbBase = new HashMap<>(base);
        pbBase.put("entry_mode", "pullback");
        pbBase.put("min_trend_bars", 3);
        pbBase.put("max_pullback_bars", 2);
        ha(s, "HA_R20", "PB_3_2", pbBase);
        pbBase.put("min_trend_bars", 5);
        ha(s, "HA_R20", "PB_5_2", new HashMap<>(pbBase));
        pbBase.put("max_pullback_bars", 3);
        ha(s, "HA_R20", "PB_5_3", new HashMap<>(pbBase));
        pbBase.put("min_trend_bars", 3);
        pbBase.put("max_pullback_bars", 1);
        ha(s, "HA_R20", "PB_3_1", new HashMap<>(pbBase));

        // ── D: RSI pullback entry + CD15 ──────────────────────────
        Map<String, Object> rsiBase = new HashMap<>(base);
        rsiBase.put("entry_mode", "rsi_pullback");
        rsiBase.put("rsi_period", 2);
        for (double rsi : new double[]{40.0, 45.0, 50.0, 55.0, 60.0}) {
            ha(s, "HA_R20", String.format("RSI%.0f", rsi), rsiBase, "rsi_entry", rsi);
        }

        // ── E: Regime entry + CD15 ────────────────────────────────
        Map<String, Object> regBase = new HashMap<>(base);
        regBase.put("entry_mode", "regime");
        regBase.put("regime_window", 10);
        regBase.put("regime_threshold", 0.6);
        regBase.put("rsi_entry", 55.0);
        regBase.put("rsi_period", 2);
        ha(s, "HA_R20", "REG_W10_T0.6", regBase);
        ha(s, "HA_R20", "REG_W10_T0.5", regBase, "regime_threshold", 0.5);
        ha(s, "HA_R20", "REG_W5_T0.6", regBase, "regime_window", 5);

        // ── F: Very wide SL/TR with CD15 (extreme patience) ──────
        for (double sl : new double[]{8.0, 10.0, 12.0}) {
            for (double tr : new double[]{8.0, 10.0, 12.0}) {
                String name = String.format("SL%.0f_TR%.0f", sl, tr);
                ha(s, "HA_R20", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── G: Confirm bars 3-5 + longer cooldown ────────────────
        for (int cb : new int[]{3, 4, 5}) {
            for (int cd : new int[]{15, 20, 25}) {
                ha(s, "HA_R20", "CB" + cb + "_CD" + cd, base,
                        "confirm_bars", cb, "cooldown_bars", cd);
            }
        }

        // ── H: No confirm bars (CB0/CB1) + longer cooldown ───────
        // Maybe confirm_bars=2 is too strict?
        ha(s, "HA_R20", "CB0_CD15", base, "confirm_bars", 0);
        ha(s, "HA_R20", "CB0_CD20", base, "confirm_bars", 0, "cooldown_bars", 20);
        ha(s, "HA_R20", "CB1_CD15", base, "confirm_bars", 1);
        ha(s, "HA_R20", "CB1_CD20", base, "confirm_bars", 1, "cooldown_bars", 20);

        // ── I: Asymmetric SL/TR ratios ────────────────────────────
        // Try very tight SL with very wide trail and vice versa
        ha(s, "HA_R20", "SL2_TR10", base, "sl_atr_mult", 2.0, "trail_atr_mult", 10.0);
        ha(s, "HA_R20", "SL3_TR10", base, "sl_atr_mult", 3.0, "trail_atr_mult", 10.0);
        ha(s, "HA_R20", "SL3_TR8", base, "sl_atr_mult", 3.0, "trail_atr_mult", 8.0);
        ha(s, "HA_R20", "SL4_TR8", base, "sl_atr_mult", 4.0, "trail_atr_mult", 8.0);
        ha(s, "HA_R20", "SL4_TR10", base, "sl_atr_mult", 4.0, "trail_atr_mult", 10.0);

        // ── J: High activation % (let winners run further) ────────
        for (double a : new double[]{10.0, 15.0, 20.0}) {
            ha(s, "HA_R20", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── K: Trend EMA as entry filter + CD15 ──────────────────
        ha(s, "HA_R20", "EMA20", base, "trend_ema", 20);
        ha(s, "HA_R20", "EMA50", base, "trend_ema", 50);
        ha(s, "HA_R20", "EMA100", base, "trend_ema", 100);

        // ── L: EMA slope filter + CD15 ────────────────────────────
        ha(s, "HA_R20", "EMA20_SLP3", base, "trend_ema", 20, "ema_slope_bars", 3);
        ha(s, "HA_R20", "EMA50_SLP5", base, "trend_ema", 50, "ema_slope_bars", 5);

        // ── M: TP (take profit) experiments ───────────────────────
        // Maybe partial exits via TP helps lock in some gains?
        for (double tp : new double[]{10.0, 15.0, 20.0, 30.0}) {
            ha(s, "HA_R20", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R21: REGIME ENTRY deep dive + TP30 combos
    //  REG_W5_T0.6 = 74.6 (MDD -43.3%), REG_W10_T0.6 = 73.6 (MDD -41.7%)
    //  Also: flip entry + TP30 = 74.6
    // ══════════════════════════════════════════════════════════════
    private static void addR21Push(Map<String, Supplier<Strategy>> s) {

        // ── REGIME BASE ───────────────────────────────────────────
        Map<String, Object> regBase = new HashMap<>();
        regBase.put("entry_mode", "regime");
        regBase.put("ha_flip_exit", false);
        regBase.put("tp_atr_mult", 0.0);
        regBase.put("trailing_stop", true);
        regBase.put("confirm_bars", 2);
        regBase.put("sl_atr_mult", 5.5);
        regBase.put("trail_atr_mult", 6.0);
        regBase.put("trail_activation_pct", 3.0);
        regBase.put("cooldown_bars", 15);
        regBase.put("max_consec_losses", 3);
        regBase.put("rsi_period", 2);
        regBase.put("rsi_entry", 55.0);

        // ── A: Regime window × threshold sweep ────────────────────
        int[] windows = {3, 5, 7, 10, 15, 20};
        double[] thresholds = {0.4, 0.5, 0.6, 0.7, 0.8};
        for (int w : windows) {
            for (double t : thresholds) {
                String name = String.format("W%d_T%.1f", w, t);
                ha(s, "HA_R21", name, regBase,
                        "regime_window", w, "regime_threshold", t);
            }
        }

        // ── B: RSI entry threshold sweep with best regime params ──
        double[] rsis = {40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0};
        for (double rsi : rsis) {
            ha(s, "HA_R21", String.format("W5_T0.6_RSI%.0f", rsi), regBase,
                    "regime_window", 5, "regime_threshold", 0.6, "rsi_entry", rsi);
            ha(s, "HA_R21", String.format("W10_T0.6_RSI%.0f", rsi), regBase,
                    "regime_window", 10, "regime_threshold", 0.6, "rsi_entry", rsi);
        }

        // ── C: Regime + SL/TR variations ──────────────────────────
        double[] sls = {4.0, 5.0, 5.5, 6.0, 7.0, 8.0};
        double[] trs = {5.0, 6.0, 7.0, 8.0};
        for (double sl : sls) {
            for (double tr : trs) {
                ha(s, "HA_R21", String.format("W5_SL%.0f_TR%.0f", sl, tr), regBase,
                        "regime_window", 5, "regime_threshold", 0.6,
                        "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── D: Regime + cooldown variations ───────────────────────
        for (int cd : new int[]{10, 15, 20, 25}) {
            ha(s, "HA_R21", "W5_CD" + cd, regBase,
                    "regime_window", 5, "regime_threshold", 0.6, "cooldown_bars", cd);
            ha(s, "HA_R21", "W10_CD" + cd, regBase,
                    "regime_window", 10, "regime_threshold", 0.6, "cooldown_bars", cd);
        }

        // ── E: Regime + activation sweeps ─────────────────────────
        for (double a : new double[]{0.0, 2.0, 4.0, 5.0}) {
            ha(s, "HA_R21", String.format("W5_A%.0f", a), regBase,
                    "regime_window", 5, "regime_threshold", 0.6, "trail_activation_pct", a);
        }

        // ── F: Regime + TP30 ──────────────────────────────────────
        ha(s, "HA_R21", "W5_TP30", regBase,
                "regime_window", 5, "regime_threshold", 0.6, "tp_atr_mult", 30.0);
        ha(s, "HA_R21", "W10_TP30", regBase,
                "regime_window", 10, "regime_threshold", 0.6, "tp_atr_mult", 30.0);
        ha(s, "HA_R21", "W5_TP20", regBase,
                "regime_window", 5, "regime_threshold", 0.6, "tp_atr_mult", 20.0);

        // ── G: Regime + confirm bars ──────────────────────────────
        for (int cb : new int[]{0, 1, 3}) {
            ha(s, "HA_R21", "W5_CB" + cb, regBase,
                    "regime_window", 5, "regime_threshold", 0.6, "confirm_bars", cb);
            ha(s, "HA_R21", "W10_CB" + cb, regBase,
                    "regime_window", 10, "regime_threshold", 0.6, "confirm_bars", cb);
        }

        // ── H: Flip entry + TP30 fine-tune ───────────────────────
        Map<String, Object> flipTp = new HashMap<>();
        flipTp.put("entry_mode", "flip");
        flipTp.put("ha_flip_exit", false);
        flipTp.put("trailing_stop", true);
        flipTp.put("confirm_bars", 2);
        flipTp.put("sl_atr_mult", 5.5);
        flipTp.put("trail_atr_mult", 6.0);
        flipTp.put("trail_activation_pct", 3.0);
        flipTp.put("cooldown_bars", 15);
        flipTp.put("max_consec_losses", 3);
        flipTp.put("tp_atr_mult", 30.0);

        // TP value sweep
        for (double tp : new double[]{25.0, 30.0, 35.0, 40.0, 50.0}) {
            ha(s, "HA_R21", String.format("FLIP_TP%.0f", tp), flipTp, "tp_atr_mult", tp);
        }
        // TP30 + SL/TR variations
        ha(s, "HA_R21", "FLIP_TP30_SL6_TR6", flipTp, "sl_atr_mult", 6.0, "trail_atr_mult", 6.0);
        ha(s, "HA_R21", "FLIP_TP30_SL5_TR6", flipTp, "sl_atr_mult", 5.0, "trail_atr_mult", 6.0);
        ha(s, "HA_R21", "FLIP_TP30_SL6_TR7", flipTp, "sl_atr_mult", 6.0, "trail_atr_mult", 7.0);
        // TP30 + activation
        ha(s, "HA_R21", "FLIP_TP30_A4", flipTp, "trail_activation_pct", 4.0);
        ha(s, "HA_R21", "FLIP_TP30_A5", flipTp, "trail_activation_pct", 5.0);
        // TP30 + ATR10
        ha(s, "HA_R21", "FLIP_TP30_ATR10", flipTp, "atr_period", 10);
        // TP30 + CD variations
        ha(s, "HA_R21", "FLIP_TP30_CD20", flipTp, "cooldown_bars", 20);
        ha(s, "HA_R21", "FLIP_TP30_CD10", flipTp, "cooldown_bars", 10);
    }

    // ══════════════════════════════════════════════════════════════
    //  R22: FINE-TUNE regime W10_T0.6_RSI40 (Score 75.3, MDD -40.87%)
    //  Also explore W7_T0.5 (75.2) and W5_CD20 (75.2)
    // ══════════════════════════════════════════════════════════════
    private static void addR22Push(Map<String, Supplier<Strategy>> s) {

        // ── Regime base: W10_T0.6_RSI40 ──────────────────────────
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 5.5);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);
        base.put("cooldown_bars", 15);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.6);

        ha(s, "HA_R22", "BASE", base);

        // ── A: RSI fine-tune around 40 ────────────────────────────
        for (double rsi : new double[]{25.0, 30.0, 35.0, 38.0, 40.0, 42.0, 45.0, 50.0}) {
            ha(s, "HA_R22", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── B: RSI period sweep ───────────────────────────────────
        for (int p : new int[]{2, 3, 5, 7, 10, 14}) {
            ha(s, "HA_R22", "RSIP" + p, base, "rsi_period", p);
        }

        // ── C: Window fine-tune ───────────────────────────────────
        for (int w : new int[]{7, 8, 9, 10, 11, 12, 15}) {
            ha(s, "HA_R22", "W" + w, base, "regime_window", w);
        }

        // ── D: Threshold fine-tune ────────────────────────────────
        for (double t : new double[]{0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7}) {
            ha(s, "HA_R22", String.format("T%.2f", t), base, "regime_threshold", t);
        }

        // ── E: SL/TR grid with regime entry ───────────────────────
        double[] sls = {4.0, 5.0, 5.5, 6.0, 6.5, 7.0, 8.0};
        double[] trs = {5.0, 6.0, 6.5, 7.0, 8.0};
        for (double sl : sls) {
            for (double tr : trs) {
                String name = String.format("SL%.1f_TR%.1f", sl, tr);
                ha(s, "HA_R22", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── F: Activation sweep ───────────────────────────────────
        for (double a : new double[]{0.0, 1.0, 2.0, 4.0, 5.0, 7.0}) {
            ha(s, "HA_R22", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── G: Cooldown sweep ─────────────────────────────────────
        for (int cd : new int[]{10, 12, 15, 18, 20, 25, 30}) {
            ha(s, "HA_R22", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── H: Max consec losses ──────────────────────────────────
        for (int l : new int[]{1, 2, 3, 4, 5}) {
            ha(s, "HA_R22", "L" + l, base, "max_consec_losses", l);
        }

        // ── I: ATR period ─────────────────────────────────────────
        ha(s, "HA_R22", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R22", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R22", "ATR20", base, "atr_period", 20);

        // ── J: Confirm bars ───────────────────────────────────────
        ha(s, "HA_R22", "CB0", base, "confirm_bars", 0);
        ha(s, "HA_R22", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R22", "CB3", base, "confirm_bars", 3);

        // ── K: TP experiments ─────────────────────────────────────
        for (double tp : new double[]{20.0, 30.0, 40.0, 50.0}) {
            ha(s, "HA_R22", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── L: Combos of best individual mods ─────────────────────
        // RSI35 + various
        ha(s, "HA_R22", "RSI35_CD20", base, "rsi_entry", 35.0, "cooldown_bars", 20);
        ha(s, "HA_R22", "RSI35_SL6_TR6", base, "rsi_entry", 35.0,
                "sl_atr_mult", 6.0, "trail_atr_mult", 6.0);
        ha(s, "HA_R22", "RSI35_A4", base, "rsi_entry", 35.0, "trail_activation_pct", 4.0);
        ha(s, "HA_R22", "RSI35_ATR10", base, "rsi_entry", 35.0, "atr_period", 10);

        // W7 variants (2nd best window from R21)
        ha(s, "HA_R22", "W7_RSI40", base, "regime_window", 7, "rsi_entry", 40.0);
        ha(s, "HA_R22", "W7_RSI35", base, "regime_window", 7, "rsi_entry", 35.0);
        ha(s, "HA_R22", "W7_T0.5_RSI40", base, "regime_window", 7,
                "regime_threshold", 0.5, "rsi_entry", 40.0);
        ha(s, "HA_R22", "W7_T0.5_RSI35", base, "regime_window", 7,
                "regime_threshold", 0.5, "rsi_entry", 35.0);

        // ADX
        ha(s, "HA_R22", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R22", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R23: FINE-TUNE regime W10_T0.6_RSI40 + SL6_TR6 (75.8)
    //  Combine top mods: A0/A1, CD18, ATR10, TP30
    // ══════════════════════════════════════════════════════════════
    private static void addR23Push(Map<String, Supplier<Strategy>> s) {

        // New winner base: regime + SL6_TR6
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 6.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 3.0);
        base.put("cooldown_bars", 15);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.6);

        ha(s, "HA_R23", "BASE", base);

        // ── A: Stack top mods on SL6_TR6 ──────────────────────────
        // A0 + SL6_TR6
        ha(s, "HA_R23", "A0", base, "trail_activation_pct", 0.0);
        ha(s, "HA_R23", "A1", base, "trail_activation_pct", 1.0);
        ha(s, "HA_R23", "A2", base, "trail_activation_pct", 2.0);
        // CD18 + SL6_TR6
        ha(s, "HA_R23", "CD18", base, "cooldown_bars", 18);
        ha(s, "HA_R23", "CD20", base, "cooldown_bars", 20);
        // ATR10 + SL6_TR6
        ha(s, "HA_R23", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R23", "ATR7", base, "atr_period", 7);
        // TP30 + SL6_TR6
        ha(s, "HA_R23", "TP30", base, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "TP50", base, "tp_atr_mult", 50.0);

        // ── B: Double combos ──────────────────────────────────────
        ha(s, "HA_R23", "A0_CD18", base, "trail_activation_pct", 0.0, "cooldown_bars", 18);
        ha(s, "HA_R23", "A1_CD18", base, "trail_activation_pct", 1.0, "cooldown_bars", 18);
        ha(s, "HA_R23", "A0_ATR10", base, "trail_activation_pct", 0.0, "atr_period", 10);
        ha(s, "HA_R23", "A1_ATR10", base, "trail_activation_pct", 1.0, "atr_period", 10);
        ha(s, "HA_R23", "CD18_ATR10", base, "cooldown_bars", 18, "atr_period", 10);
        ha(s, "HA_R23", "A0_TP30", base, "trail_activation_pct", 0.0, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "A1_TP30", base, "trail_activation_pct", 1.0, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "CD18_TP30", base, "cooldown_bars", 18, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "ATR10_TP30", base, "atr_period", 10, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "CD20_ATR10", base, "cooldown_bars", 20, "atr_period", 10);
        ha(s, "HA_R23", "CD20_A0", base, "cooldown_bars", 20, "trail_activation_pct", 0.0);

        // ── C: Triple combos ──────────────────────────────────────
        ha(s, "HA_R23", "A0_CD18_ATR10", base,
                "trail_activation_pct", 0.0, "cooldown_bars", 18, "atr_period", 10);
        ha(s, "HA_R23", "A1_CD18_ATR10", base,
                "trail_activation_pct", 1.0, "cooldown_bars", 18, "atr_period", 10);
        ha(s, "HA_R23", "A0_CD18_TP30", base,
                "trail_activation_pct", 0.0, "cooldown_bars", 18, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "A1_CD18_TP30", base,
                "trail_activation_pct", 1.0, "cooldown_bars", 18, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "A0_ATR10_TP30", base,
                "trail_activation_pct", 0.0, "atr_period", 10, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "CD18_ATR10_TP30", base,
                "cooldown_bars", 18, "atr_period", 10, "tp_atr_mult", 30.0);

        // ── D: Quad combo ─────────────────────────────────────────
        ha(s, "HA_R23", "A0_CD18_ATR10_TP30", base,
                "trail_activation_pct", 0.0, "cooldown_bars", 18,
                "atr_period", 10, "tp_atr_mult", 30.0);
        ha(s, "HA_R23", "A1_CD18_ATR10_TP30", base,
                "trail_activation_pct", 1.0, "cooldown_bars", 18,
                "atr_period", 10, "tp_atr_mult", 30.0);

        // ── E: SL/TR fine-tune around 6.0 ─────────────────────────
        double[] sls = {5.5, 5.75, 6.0, 6.25, 6.5, 7.0};
        double[] trs = {5.5, 5.75, 6.0, 6.25, 6.5, 7.0};
        for (double sl : sls) {
            for (double tr : trs) {
                if (sl == 6.0 && tr == 6.0) continue; // skip base
                String name = String.format("SL%.2f_TR%.2f", sl, tr);
                ha(s, "HA_R23", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── F: RSI fine-tune with SL6_TR6 ─────────────────────────
        for (double rsi : new double[]{30.0, 35.0, 38.0, 42.0, 45.0}) {
            ha(s, "HA_R23", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── G: Window/threshold fine-tune ─────────────────────────
        ha(s, "HA_R23", "W8", base, "regime_window", 8);
        ha(s, "HA_R23", "W9", base, "regime_window", 9);
        ha(s, "HA_R23", "W11", base, "regime_window", 11);
        ha(s, "HA_R23", "W12", base, "regime_window", 12);
        ha(s, "HA_R23", "T0.5", base, "regime_threshold", 0.5);
        ha(s, "HA_R23", "T0.55", base, "regime_threshold", 0.55);
        ha(s, "HA_R23", "T0.65", base, "regime_threshold", 0.65);

        // ── H: L sweep ────────────────────────────────────────────
        ha(s, "HA_R23", "L1", base, "max_consec_losses", 1);
        ha(s, "HA_R23", "L2", base, "max_consec_losses", 2);
        ha(s, "HA_R23", "L4", base, "max_consec_losses", 4);
        ha(s, "HA_R23", "L5", base, "max_consec_losses", 5);
    }

    // ══════════════════════════════════════════════════════════════
    //  R24: FINE-TUNE A1_CD18 winner (76.3)
    //  Base: regime W10_T0.6_RSI40 + SL6_TR6 + A1 + CD18
    // ══════════════════════════════════════════════════════════════
    private static void addR24Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 6.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 1.0);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.6);

        ha(s, "HA_R24", "BASE", base);

        // ── A: Ultra-fine activation (0.25 steps) ─────────────────
        for (double a : new double[]{0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0}) {
            ha(s, "HA_R24", String.format("A%.2f", a), base, "trail_activation_pct", a);
        }

        // ── B: Cooldown fine-tune (1-step around 18) ──────────────
        for (int cd : new int[]{14, 15, 16, 17, 18, 19, 20, 22, 25}) {
            ha(s, "HA_R24", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── C: SL/TR fine grid with A1_CD18 ──────────────────────
        double[] sls = {5.0, 5.5, 5.75, 6.0, 6.25, 6.5, 7.0, 7.5, 8.0};
        double[] trs = {5.0, 5.5, 5.75, 6.0, 6.25, 6.5, 7.0, 7.5, 8.0};
        for (double sl : sls) {
            for (double tr : trs) {
                if (sl == 6.0 && tr == 6.0) continue;
                String name = String.format("SL%.1f_TR%.1f", sl, tr);
                ha(s, "HA_R24", name, base, "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── D: RSI fine-tune ──────────────────────────────────────
        for (double rsi : new double[]{25.0, 30.0, 35.0, 38.0, 42.0, 45.0, 50.0}) {
            ha(s, "HA_R24", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── E: RSI period ─────────────────────────────────────────
        for (int p : new int[]{3, 5, 7, 10}) {
            ha(s, "HA_R24", "RSIP" + p, base, "rsi_period", p);
        }

        // ── F: Window/threshold ───────────────────────────────────
        for (int w : new int[]{7, 8, 9, 11, 12, 15}) {
            ha(s, "HA_R24", "W" + w, base, "regime_window", w);
        }
        for (double t : new double[]{0.4, 0.5, 0.55, 0.65, 0.7}) {
            ha(s, "HA_R24", String.format("T%.2f", t), base, "regime_threshold", t);
        }

        // ── G: ATR period ─────────────────────────────────────────
        ha(s, "HA_R24", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R24", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R24", "ATR20", base, "atr_period", 20);

        // ── H: Max consec losses ──────────────────────────────────
        for (int l : new int[]{1, 2, 4, 5}) {
            ha(s, "HA_R24", "L" + l, base, "max_consec_losses", l);
        }

        // ── I: TP sweep ───────────────────────────────────────────
        for (double tp : new double[]{20.0, 25.0, 30.0, 40.0, 50.0}) {
            ha(s, "HA_R24", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── J: Best combos from individual sweeps ─────────────────
        // CD + RSI combos
        ha(s, "HA_R24", "CD20_RSI35", base, "cooldown_bars", 20, "rsi_entry", 35.0);
        ha(s, "HA_R24", "CD20_RSI38", base, "cooldown_bars", 20, "rsi_entry", 38.0);
        ha(s, "HA_R24", "CD16_RSI35", base, "cooldown_bars", 16, "rsi_entry", 35.0);
        // SL/TR + ATR10
        ha(s, "HA_R24", "SL6_TR6.5_ATR10", base, "trail_atr_mult", 6.5, "atr_period", 10);
        ha(s, "HA_R24", "SL6.5_TR6_ATR10", base, "sl_atr_mult", 6.5, "atr_period", 10);
        ha(s, "HA_R24", "SL7_TR6_ATR10", base, "sl_atr_mult", 7.0, "atr_period", 10);
        // TP + CD combos
        ha(s, "HA_R24", "TP30_CD20", base, "tp_atr_mult", 30.0, "cooldown_bars", 20);
        ha(s, "HA_R24", "TP50_CD20", base, "tp_atr_mult", 50.0, "cooldown_bars", 20);
        // Strong candle
        ha(s, "HA_R24", "STRONG", base, "strong_candle", true);
        // ADX
        ha(s, "HA_R24", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R24", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R25: STACK TOP MODS — SL7 + ADX10 + A0.25 + combos
    //  Base: regime W10_T0.6_RSI40 + SL6_TR6 + A1 + CD18
    // ══════════════════════════════════════════════════════════════
    private static void addR25Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 6.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 1.0);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.6);

        // ── A: Stack SL7 + ADX10 ─────────────────────────────────
        ha(s, "HA_R25", "SL7_ADX10", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R25", "SL7_ADX15", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 15.0);

        // ── B: Stack SL7 + A0.25 ─────────────────────────────────
        ha(s, "HA_R25", "SL7_A0.25", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25);

        // ── C: Stack ADX10 + A0.25 ───────────────────────────────
        ha(s, "HA_R25", "ADX10_A0.25", base,
                "adx_filter", true, "adx_threshold", 10.0, "trail_activation_pct", 0.25);

        // ── D: Triple stack ───────────────────────────────────────
        ha(s, "HA_R25", "SL7_ADX10_A0.25", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 10.0,
                "trail_activation_pct", 0.25);
        ha(s, "HA_R25", "SL7_ADX15_A0.25", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 15.0,
                "trail_activation_pct", 0.25);

        // ── E: SL7 + TP ──────────────────────────────────────────
        ha(s, "HA_R25", "SL7_TP25", base, "sl_atr_mult", 7.0, "tp_atr_mult", 25.0);
        ha(s, "HA_R25", "SL7_TP30", base, "sl_atr_mult", 7.0, "tp_atr_mult", 30.0);
        ha(s, "HA_R25", "SL7_TP50", base, "sl_atr_mult", 7.0, "tp_atr_mult", 50.0);

        // ── F: SL7 + TR fine-tune ─────────────────────────────────
        for (double tr : new double[]{5.5, 5.75, 6.0, 6.25, 6.5, 7.0, 7.5}) {
            ha(s, "HA_R25", String.format("SL7_TR%.2f", tr), base,
                    "sl_atr_mult", 7.0, "trail_atr_mult", tr);
        }

        // ── G: SL fine-tune around 7 ─────────────────────────────
        for (double sl : new double[]{6.5, 6.75, 7.0, 7.25, 7.5, 8.0}) {
            ha(s, "HA_R25", String.format("SL%.2f", sl), base, "sl_atr_mult", sl);
        }

        // ── H: SL7 + CD sweep ────────────────────────────────────
        for (int cd : new int[]{15, 16, 18, 20, 22, 25}) {
            ha(s, "HA_R25", "SL7_CD" + cd, base, "sl_atr_mult", 7.0, "cooldown_bars", cd);
        }

        // ── I: SL7 + RSI fine-tune ───────────────────────────────
        for (double rsi : new double[]{30.0, 35.0, 38.0, 42.0, 45.0}) {
            ha(s, "HA_R25", String.format("SL7_RSI%.0f", rsi), base,
                    "sl_atr_mult", 7.0, "rsi_entry", rsi);
        }

        // ── J: SL7 + window/threshold ────────────────────────────
        ha(s, "HA_R25", "SL7_W8", base, "sl_atr_mult", 7.0, "regime_window", 8);
        ha(s, "HA_R25", "SL7_W9", base, "sl_atr_mult", 7.0, "regime_window", 9);
        ha(s, "HA_R25", "SL7_W12", base, "sl_atr_mult", 7.0, "regime_window", 12);
        ha(s, "HA_R25", "SL7_T0.5", base, "sl_atr_mult", 7.0, "regime_threshold", 0.5);
        ha(s, "HA_R25", "SL7_T0.7", base, "sl_atr_mult", 7.0, "regime_threshold", 0.7);

        // ── K: SL7 + ATR period ───────────────────────────────────
        ha(s, "HA_R25", "SL7_ATR7", base, "sl_atr_mult", 7.0, "atr_period", 7);
        ha(s, "HA_R25", "SL7_ATR10", base, "sl_atr_mult", 7.0, "atr_period", 10);
        ha(s, "HA_R25", "SL7_ATR20", base, "sl_atr_mult", 7.0, "atr_period", 20);

        // ── L: SL7 + L sweep ──────────────────────────────────────
        ha(s, "HA_R25", "SL7_L2", base, "sl_atr_mult", 7.0, "max_consec_losses", 2);
        ha(s, "HA_R25", "SL7_L4", base, "sl_atr_mult", 7.0, "max_consec_losses", 4);

        // ── M: SL7 multi-combos ───────────────────────────────────
        ha(s, "HA_R25", "SL7_A0.25_CD20", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25, "cooldown_bars", 20);
        ha(s, "HA_R25", "SL7_A0.25_TP25", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25, "tp_atr_mult", 25.0);
        ha(s, "HA_R25", "SL7_ADX10_TP25", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 10.0, "tp_atr_mult", 25.0);
        ha(s, "HA_R25", "SL7_ADX10_CD20", base,
                "sl_atr_mult", 7.0, "adx_filter", true, "adx_threshold", 10.0, "cooldown_bars", 20);
        ha(s, "HA_R25", "SL7_ATR10_ADX10", base,
                "sl_atr_mult", 7.0, "atr_period", 10,
                "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R25", "SL7_A0.25_ATR10", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25, "atr_period", 10);
        ha(s, "HA_R25", "SL7_RSI35_CD20", base,
                "sl_atr_mult", 7.0, "rsi_entry", 35.0, "cooldown_bars", 20);
        ha(s, "HA_R25", "SL7_RSI38_ADX10", base,
                "sl_atr_mult", 7.0, "rsi_entry", 38.0,
                "adx_filter", true, "adx_threshold", 10.0);

        // ── N: Quad+ combos ───────────────────────────────────────
        ha(s, "HA_R25", "SL7_A0.25_ADX10_TP25", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25,
                "adx_filter", true, "adx_threshold", 10.0, "tp_atr_mult", 25.0);
        ha(s, "HA_R25", "SL7_A0.25_ADX10_CD20", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25,
                "adx_filter", true, "adx_threshold", 10.0, "cooldown_bars", 20);
        ha(s, "HA_R25", "SL7_A0.25_ATR10_ADX10", base,
                "sl_atr_mult", 7.0, "trail_activation_pct", 0.25,
                "atr_period", 10, "adx_filter", true, "adx_threshold", 10.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R26: PUSH PAST 76.9 — fine-tune SL7_A0.25 winner
    // ══════════════════════════════════════════════════════════════
    private static void addR26Push(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 7.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.6);

        ha(s, "HA_R26", "BASE", base);

        // ── A: SL fine-tune (0.5 steps around 7) ─────────────────
        for (double sl : new double[]{6.5, 6.75, 7.0, 7.25, 7.5, 8.0, 8.5, 9.0, 10.0}) {
            ha(s, "HA_R26", String.format("SL%.1f", sl), base, "sl_atr_mult", sl);
        }

        // ── B: TR fine-tune ───────────────────────────────────────
        for (double tr : new double[]{5.0, 5.5, 5.75, 6.0, 6.25, 6.5, 7.0, 7.5, 8.0}) {
            ha(s, "HA_R26", String.format("TR%.1f", tr), base, "trail_atr_mult", tr);
        }

        // ── C: Activation micro-tune ──────────────────────────────
        for (double a : new double[]{0.0, 0.1, 0.15, 0.2, 0.25, 0.3, 0.5, 0.75}) {
            ha(s, "HA_R26", String.format("A%.2f", a), base, "trail_activation_pct", a);
        }

        // ── D: CD sweep ───────────────────────────────────────────
        for (int cd : new int[]{15, 16, 17, 18, 19, 20, 22, 25}) {
            ha(s, "HA_R26", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── E: RSI tune ───────────────────────────────────────────
        for (double rsi : new double[]{30.0, 35.0, 38.0, 40.0, 42.0, 45.0}) {
            ha(s, "HA_R26", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── F: Window/threshold ───────────────────────────────────
        for (int w : new int[]{7, 8, 9, 11, 12}) {
            ha(s, "HA_R26", "W" + w, base, "regime_window", w);
        }
        for (double t : new double[]{0.4, 0.5, 0.55, 0.65, 0.7}) {
            ha(s, "HA_R26", String.format("T%.2f", t), base, "regime_threshold", t);
        }

        // ── G: TP sweep ───────────────────────────────────────────
        for (double tp : new double[]{20.0, 25.0, 30.0, 40.0, 50.0, 75.0}) {
            ha(s, "HA_R26", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── H: ATR period ─────────────────────────────────────────
        for (int atr : new int[]{7, 10, 20}) {
            ha(s, "HA_R26", "ATR" + atr, base, "atr_period", atr);
        }

        // ── I: L sweep ────────────────────────────────────────────
        for (int l : new int[]{1, 2, 4, 5}) {
            ha(s, "HA_R26", "L" + l, base, "max_consec_losses", l);
        }

        // ── J: ADX ────────────────────────────────────────────────
        ha(s, "HA_R26", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R26", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);

        // ── K: Best SL/TR combos ──────────────────────────────────
        double[][] pairs = {{7.0, 5.5}, {7.0, 6.5}, {7.0, 7.0}, {7.5, 6.0}, {7.5, 6.5},
                {8.0, 6.0}, {8.0, 6.5}, {8.0, 7.0}, {6.5, 6.5}, {9.0, 6.0}};
        for (double[] p : pairs) {
            String name = String.format("SL%.1f_TR%.1f", p[0], p[1]);
            ha(s, "HA_R26", name, base, "sl_atr_mult", p[0], "trail_atr_mult", p[1]);
        }

        // ── L: Combos of best individual mods ─────────────────────
        ha(s, "HA_R26", "SL7.5_TP50", base, "sl_atr_mult", 7.5, "tp_atr_mult", 50.0);
        ha(s, "HA_R26", "SL8_TP50", base, "sl_atr_mult", 8.0, "tp_atr_mult", 50.0);
        ha(s, "HA_R26", "SL7_TP50_CD20", base, "tp_atr_mult", 50.0, "cooldown_bars", 20);
        ha(s, "HA_R26", "SL7_ADX10_TP50", base,
                "adx_filter", true, "adx_threshold", 10.0, "tp_atr_mult", 50.0);
        ha(s, "HA_R26", "SL7.5_ADX10", base,
                "sl_atr_mult", 7.5, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R26", "SL8_ADX10", base,
                "sl_atr_mult", 8.0, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R26", "RSI35_CD20", base, "rsi_entry", 35.0, "cooldown_bars", 20);
        ha(s, "HA_R26", "RSI35_TP50", base, "rsi_entry", 35.0, "tp_atr_mult", 50.0);
        ha(s, "HA_R26", "W8_RSI35", base, "regime_window", 8, "rsi_entry", 35.0);
        ha(s, "HA_R26", "W8_T0.5", base, "regime_window", 8, "regime_threshold", 0.5);

        // ── M: Confirm bars ───────────────────────────────────────
        ha(s, "HA_R26", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R26", "CB3", base, "confirm_bars", 3);
        ha(s, "HA_R26", "CB0", base, "confirm_bars", 0);

        // ── N: RSI period ─────────────────────────────────────────
        for (int p : new int[]{3, 5, 7}) {
            ha(s, "HA_R26", "RSIP" + p, base, "rsi_period", p);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R27: FINE-TUNE T0.40 (77.7) and TP50 combos (77.6)
    // ══════════════════════════════════════════════════════════════
    private static void addR27Push(Map<String, Supplier<Strategy>> s) {

        // ── T0.40 base (new winner) ───────────────────────────────
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 7.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 40.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.4);

        ha(s, "HA_R27", "BASE", base);

        // ── A: Threshold fine-tune ────────────────────────────────
        for (double t : new double[]{0.3, 0.35, 0.4, 0.45, 0.5}) {
            ha(s, "HA_R27", String.format("T%.2f", t), base, "regime_threshold", t);
        }

        // ── B: SL fine-tune ───────────────────────────────────────
        for (double sl : new double[]{5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 9.0, 10.0}) {
            ha(s, "HA_R27", String.format("SL%.1f", sl), base, "sl_atr_mult", sl);
        }

        // ── C: TR fine-tune ───────────────────────────────────────
        for (double tr : new double[]{5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0}) {
            ha(s, "HA_R27", String.format("TR%.1f", tr), base, "trail_atr_mult", tr);
        }

        // ── D: TP sweep ───────────────────────────────────────────
        for (double tp : new double[]{20.0, 25.0, 30.0, 40.0, 50.0, 75.0, 100.0}) {
            ha(s, "HA_R27", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── E: CD sweep ───────────────────────────────────────────
        for (int cd : new int[]{10, 15, 18, 20, 22, 25, 30}) {
            ha(s, "HA_R27", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── F: RSI sweep ──────────────────────────────────────────
        for (double rsi : new double[]{25.0, 30.0, 35.0, 38.0, 42.0, 45.0, 50.0}) {
            ha(s, "HA_R27", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── G: Window sweep ───────────────────────────────────────
        for (int w : new int[]{5, 7, 8, 9, 12, 15}) {
            ha(s, "HA_R27", "W" + w, base, "regime_window", w);
        }

        // ── H: Activation ─────────────────────────────────────────
        for (double a : new double[]{0.0, 0.5, 1.0, 2.0, 3.0}) {
            ha(s, "HA_R27", String.format("A%.1f", a), base, "trail_activation_pct", a);
        }

        // ── I: ATR period ─────────────────────────────────────────
        ha(s, "HA_R27", "ATR7", base, "atr_period", 7);
        ha(s, "HA_R27", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R27", "ATR20", base, "atr_period", 20);

        // ── J: Max consec losses ──────────────────────────────────
        for (int l : new int[]{1, 2, 4, 5}) {
            ha(s, "HA_R27", "L" + l, base, "max_consec_losses", l);
        }

        // ── K: ADX ────────────────────────────────────────────────
        ha(s, "HA_R27", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R27", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);

        // ── L: Best combos ────────────────────────────────────────
        // TP50 + T0.40 (combine two R26 winners)
        ha(s, "HA_R27", "TP50_SL8", base, "tp_atr_mult", 50.0, "sl_atr_mult", 8.0);
        ha(s, "HA_R27", "TP50_SL7.5", base, "tp_atr_mult", 50.0, "sl_atr_mult", 7.5);
        ha(s, "HA_R27", "TP50_CD20", base, "tp_atr_mult", 50.0, "cooldown_bars", 20);
        ha(s, "HA_R27", "TP50_ADX10", base, "tp_atr_mult", 50.0,
                "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R27", "TP50_RSI35", base, "tp_atr_mult", 50.0, "rsi_entry", 35.0);
        // W8 + T0.40 (W8_T0.5 scored 77.6 in R26)
        ha(s, "HA_R27", "W8_T0.4", base, "regime_window", 8);
        ha(s, "HA_R27", "W8_TP50", base, "regime_window", 8, "tp_atr_mult", 50.0);
        ha(s, "HA_R27", "W7_TP50", base, "regime_window", 7, "tp_atr_mult", 50.0);
        // SL8 + various
        ha(s, "HA_R27", "SL8_CD20", base, "sl_atr_mult", 8.0, "cooldown_bars", 20);
        ha(s, "HA_R27", "SL8_ADX10", base, "sl_atr_mult", 8.0,
                "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R27", "SL8_RSI35", base, "sl_atr_mult", 8.0, "rsi_entry", 35.0);
        // RSI35 + CD20
        ha(s, "HA_R27", "RSI35_CD20", base, "rsi_entry", 35.0, "cooldown_bars", 20);

        // ── M: SL/TR grid with T0.40 ─────────────────────────────
        double[][] pairs = {{7.0, 7.0}, {7.5, 6.5}, {8.0, 6.0}, {8.0, 6.5}, {8.0, 7.0},
                {7.5, 7.0}, {9.0, 6.0}, {9.0, 7.0}, {6.0, 7.0}, {6.5, 7.0}};
        for (double[] p : pairs) {
            String name = String.format("SL%.1f_TR%.1f", p[0], p[1]);
            ha(s, "HA_R27", name, base, "sl_atr_mult", p[0], "trail_atr_mult", p[1]);
        }

        // ── N: CB ─────────────────────────────────────────────────
        ha(s, "HA_R27", "CB1", base, "confirm_bars", 1);
        ha(s, "HA_R27", "CB3", base, "confirm_bars", 3);
        ha(s, "HA_R27", "CB0", base, "confirm_bars", 0);

        // ── O: RSI period ─────────────────────────────────────────
        for (int p : new int[]{3, 5, 7, 14}) {
            ha(s, "HA_R27", "RSIP" + p, base, "rsi_period", p);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R28: FINE-TUNE RSI30 winner (78.4)
    //  Combine: RSI30 + W9 + SL8 + ADX10 + CD20 + L4
    // ══════════════════════════════════════════════════════════════
    private static void addR28Push(Map<String, Supplier<Strategy>> s) {

        // RSI30 base
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 7.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 3);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 30.0);
        base.put("regime_window", 10);
        base.put("regime_threshold", 0.4);

        ha(s, "HA_R28", "BASE", base);

        // ── A: RSI fine-tune around 30 ────────────────────────────
        for (double rsi : new double[]{15.0, 20.0, 25.0, 28.0, 30.0, 32.0, 35.0}) {
            ha(s, "HA_R28", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // ── B: Stack RSI30 + W9 ──────────────────────────────────
        ha(s, "HA_R28", "W9", base, "regime_window", 9);
        ha(s, "HA_R28", "W8", base, "regime_window", 8);
        ha(s, "HA_R28", "W7", base, "regime_window", 7);
        ha(s, "HA_R28", "W11", base, "regime_window", 11);

        // ── C: Stack RSI30 + SL8 ─────────────────────────────────
        ha(s, "HA_R28", "SL8", base, "sl_atr_mult", 8.0);
        ha(s, "HA_R28", "SL7.5", base, "sl_atr_mult", 7.5);
        ha(s, "HA_R28", "SL9", base, "sl_atr_mult", 9.0);
        ha(s, "HA_R28", "SL10", base, "sl_atr_mult", 10.0);

        // ── D: Stack RSI30 + ADX/CD/L mods ───────────────────────
        ha(s, "HA_R28", "ADX10", base, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R28", "CD20", base, "cooldown_bars", 20);
        ha(s, "HA_R28", "CD22", base, "cooldown_bars", 22);
        ha(s, "HA_R28", "CD25", base, "cooldown_bars", 25);
        ha(s, "HA_R28", "L4", base, "max_consec_losses", 4);
        ha(s, "HA_R28", "L2", base, "max_consec_losses", 2);

        // ── E: Double combos ──────────────────────────────────────
        ha(s, "HA_R28", "W9_SL8", base, "regime_window", 9, "sl_atr_mult", 8.0);
        ha(s, "HA_R28", "W9_ADX10", base, "regime_window", 9,
                "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R28", "W9_CD20", base, "regime_window", 9, "cooldown_bars", 20);
        ha(s, "HA_R28", "W9_L4", base, "regime_window", 9, "max_consec_losses", 4);
        ha(s, "HA_R28", "SL8_ADX10", base, "sl_atr_mult", 8.0,
                "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R28", "SL8_CD20", base, "sl_atr_mult", 8.0, "cooldown_bars", 20);
        ha(s, "HA_R28", "SL8_L4", base, "sl_atr_mult", 8.0, "max_consec_losses", 4);
        ha(s, "HA_R28", "ADX10_CD20", base, "adx_filter", true, "adx_threshold", 10.0,
                "cooldown_bars", 20);
        ha(s, "HA_R28", "ADX10_L4", base, "adx_filter", true, "adx_threshold", 10.0,
                "max_consec_losses", 4);
        ha(s, "HA_R28", "CD20_L4", base, "cooldown_bars", 20, "max_consec_losses", 4);

        // ── F: Triple combos ──────────────────────────────────────
        ha(s, "HA_R28", "W9_SL8_ADX10", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "adx_filter", true, "adx_threshold", 10.0);
        ha(s, "HA_R28", "W9_SL8_CD20", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "cooldown_bars", 20);
        ha(s, "HA_R28", "W9_SL8_L4", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "max_consec_losses", 4);
        ha(s, "HA_R28", "SL8_ADX10_CD20", base, "sl_atr_mult", 8.0,
                "adx_filter", true, "adx_threshold", 10.0, "cooldown_bars", 20);
        ha(s, "HA_R28", "SL8_ADX10_L4", base, "sl_atr_mult", 8.0,
                "adx_filter", true, "adx_threshold", 10.0, "max_consec_losses", 4);
        ha(s, "HA_R28", "W9_ADX10_L4", base, "regime_window", 9,
                "adx_filter", true, "adx_threshold", 10.0, "max_consec_losses", 4);

        // ── G: Quad combos ────────────────────────────────────────
        ha(s, "HA_R28", "W9_SL8_ADX10_L4", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "adx_filter", true, "adx_threshold", 10.0,
                "max_consec_losses", 4);
        ha(s, "HA_R28", "W9_SL8_ADX10_CD20", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "adx_filter", true, "adx_threshold", 10.0,
                "cooldown_bars", 20);
        ha(s, "HA_R28", "W9_SL8_CD20_L4", base, "regime_window", 9,
                "sl_atr_mult", 8.0, "cooldown_bars", 20, "max_consec_losses", 4);

        // ── H: TP experiments ─────────────────────────────────────
        for (double tp : new double[]{30.0, 50.0, 75.0, 100.0}) {
            ha(s, "HA_R28", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }
        ha(s, "HA_R28", "TP50_SL8", base, "tp_atr_mult", 50.0, "sl_atr_mult", 8.0);
        ha(s, "HA_R28", "TP50_W9", base, "tp_atr_mult", 50.0, "regime_window", 9);

        // ── I: TR fine-tune ───────────────────────────────────────
        for (double tr : new double[]{5.0, 5.5, 6.5, 7.0, 8.0}) {
            ha(s, "HA_R28", String.format("TR%.1f", tr), base, "trail_atr_mult", tr);
        }

        // ── J: Activation ─────────────────────────────────────────
        for (double a : new double[]{0.0, 0.5, 1.0, 2.0}) {
            ha(s, "HA_R28", String.format("A%.1f", a), base, "trail_activation_pct", a);
        }

        // ── K: ATR period ─────────────────────────────────────────
        ha(s, "HA_R28", "ATR10", base, "atr_period", 10);
        ha(s, "HA_R28", "ATR7", base, "atr_period", 7);

        // ── L: Threshold fine-tune ────────────────────────────────
        for (double t : new double[]{0.3, 0.35, 0.45, 0.5}) {
            ha(s, "HA_R28", String.format("T%.2f", t), base, "regime_threshold", t);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R29: TARGET 90+ — massive sweep around W9_SL8_L4 (80.3)
    //  Need: Calmar ~90 AND Robustness ~90
    //  Calmar = avgCalmar/0.5 → need avgCalmar ≥ 0.45
    //  Robustness = (pctProfitable + consistency) / 2
    //  Focus: maximize both CAGR/MDD ratio AND cross-asset consistency
    // ══════════════════════════════════════════════════════════════
    private static void addR29Push(Map<String, Supplier<Strategy>> s) {

        // Winner base: W9_SL8_L4
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 8.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 4);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 30.0);
        base.put("regime_window", 9);
        base.put("regime_threshold", 0.4);

        ha(s, "HA_R29", "BASE", base);

        // ── A: Every parameter individually swept ─────────────────

        // SL (wider = let trades breathe)
        for (double sl : new double[]{6.0, 7.0, 8.0, 9.0, 10.0, 12.0, 15.0, 20.0}) {
            ha(s, "HA_R29", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // TR
        for (double tr : new double[]{4.0, 5.0, 5.5, 6.0, 6.5, 7.0, 8.0, 10.0}) {
            ha(s, "HA_R29", String.format("TR%.0f", tr), base, "trail_atr_mult", tr);
        }

        // Activation
        for (double a : new double[]{0.0, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0}) {
            ha(s, "HA_R29", String.format("A%.1f", a), base, "trail_activation_pct", a);
        }

        // CD
        for (int cd : new int[]{10, 15, 18, 20, 25, 30, 40, 50}) {
            ha(s, "HA_R29", "CD" + cd, base, "cooldown_bars", cd);
        }

        // Max consec losses
        for (int l : new int[]{2, 3, 4, 5, 6, 8}) {
            ha(s, "HA_R29", "L" + l, base, "max_consec_losses", l);
        }

        // RSI
        for (double rsi : new double[]{10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0}) {
            ha(s, "HA_R29", String.format("RSI%.0f", rsi), base, "rsi_entry", rsi);
        }

        // RSI period
        for (int p : new int[]{2, 3, 5, 7, 10, 14}) {
            ha(s, "HA_R29", "RSIP" + p, base, "rsi_period", p);
        }

        // Window
        for (int w : new int[]{5, 7, 8, 9, 10, 12, 15, 20}) {
            ha(s, "HA_R29", "W" + w, base, "regime_window", w);
        }

        // Threshold
        for (double t : new double[]{0.2, 0.3, 0.35, 0.4, 0.5, 0.6, 0.7}) {
            ha(s, "HA_R29", String.format("T%.1f", t), base, "regime_threshold", t);
        }

        // Confirm bars
        for (int cb : new int[]{0, 1, 2, 3, 4, 5}) {
            ha(s, "HA_R29", "CB" + cb, base, "confirm_bars", cb);
        }

        // ATR period
        for (int atr : new int[]{5, 7, 10, 14, 20}) {
            ha(s, "HA_R29", "ATR" + atr, base, "atr_period", atr);
        }

        // TP
        for (double tp : new double[]{20.0, 30.0, 50.0, 75.0, 100.0}) {
            ha(s, "HA_R29", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ADX
        for (int adx : new int[]{10, 15, 20, 25}) {
            ha(s, "HA_R29", "ADX" + adx, base, "adx_filter", true, "adx_threshold", (double) adx);
        }

        // ── B: Top combos from R28 results ────────────────────────
        // A2 scored 79.3 — try A2 on W9_SL8_L4 base
        ha(s, "HA_R29", "A2_W9", base, "trail_activation_pct", 2.0);
        ha(s, "HA_R29", "A2_CD20", base, "trail_activation_pct", 2.0, "cooldown_bars", 20);
        ha(s, "HA_R29", "A2_ADX10", base, "trail_activation_pct", 2.0,
                "adx_filter", true, "adx_threshold", 10.0);

        // ── C: SL/TR grid with wider values ───────────────────────
        double[][] pairs = {
                {8, 7}, {8, 8}, {9, 6}, {9, 7}, {9, 8}, {10, 6}, {10, 7}, {10, 8},
                {12, 6}, {12, 8}, {12, 10}, {15, 8}, {15, 10}, {8, 5}, {7, 7}
        };
        for (double[] p : pairs) {
            ha(s, "HA_R29", String.format("SL%.0f_TR%.0f", p[0], p[1]), base,
                    "sl_atr_mult", p[0], "trail_atr_mult", p[1]);
        }

        // ── D: Best combos stacked ────────────────────────────────
        ha(s, "HA_R29", "SL10_L4_CD20", base, "sl_atr_mult", 10.0, "cooldown_bars", 20);
        ha(s, "HA_R29", "SL10_TR7_L4", base, "sl_atr_mult", 10.0, "trail_atr_mult", 7.0);
        ha(s, "HA_R29", "SL12_TR8_L4", base, "sl_atr_mult", 12.0, "trail_atr_mult", 8.0);
        ha(s, "HA_R29", "RSI20_W9", base, "rsi_entry", 20.0);
        ha(s, "HA_R29", "RSI20_SL10", base, "rsi_entry", 20.0, "sl_atr_mult", 10.0);
        ha(s, "HA_R29", "RSI25_SL10", base, "rsi_entry", 25.0, "sl_atr_mult", 10.0);
        ha(s, "HA_R29", "T0.3_W9", base, "regime_threshold", 0.3);
        ha(s, "HA_R29", "T0.3_SL10", base, "regime_threshold", 0.3, "sl_atr_mult", 10.0);

        // ── E: Trend EMA filter (boost robustness) ────────────────
        ha(s, "HA_R29", "EMA20", base, "trend_ema", 20);
        ha(s, "HA_R29", "EMA50", base, "trend_ema", 50);
        ha(s, "HA_R29", "EMA100", base, "trend_ema", 100);

        // ── F: Exit bearish bars (reduce whipsaw) ─────────────────
        for (int xb : new int[]{3, 4, 5}) {
            ha(s, "HA_R29", "XB" + xb, base, "exit_bearish_bars", xb);
        }

        // ── G: Strong candle ──────────────────────────────────────
        ha(s, "HA_R29", "STRONG", base, "strong_candle", true);
    }

    // ══════════════════════════════════════════════════════════════
    //  R30: TARGET CONSISTENCY (currently 47.7% → need ~80%)
    //  pctProfitable=100% ✓, consistency=47.7% ✗
    //  CV=0.52 (CAGR range: 2.3%-48.6%). Need to reduce variance.
    //  Strategy: (1) aggressive TP to cap outliers like NVDA
    //  (2) flip entry for more trades/statistical consistency
    //  (3) tighter risk params to reduce per-trade variance
    // ══════════════════════════════════════════════════════════════
    private static void addR30Push(Map<String, Supplier<Strategy>> s) {

        // ── A: REGIME BASE + aggressive TP sweep ──────────────────
        // Cap per-trade gains to reduce cross-asset CAGR variance
        Map<String, Object> regBase = new HashMap<>();
        regBase.put("entry_mode", "regime");
        regBase.put("ha_flip_exit", false);
        regBase.put("trailing_stop", true);
        regBase.put("confirm_bars", 2);
        regBase.put("sl_atr_mult", 8.0);
        regBase.put("trail_atr_mult", 6.0);
        regBase.put("trail_activation_pct", 0.25);
        regBase.put("cooldown_bars", 18);
        regBase.put("max_consec_losses", 4);
        regBase.put("rsi_period", 2);
        regBase.put("rsi_entry", 30.0);
        regBase.put("regime_window", 9);
        regBase.put("regime_threshold", 0.4);

        // Aggressive TP to cap winners (reduce CV)
        for (double tp : new double[]{3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 15.0}) {
            regBase.put("tp_atr_mult", tp);
            ha(s, "HA_R30", String.format("REG_TP%.0f", tp), new HashMap<>(regBase));
        }
        regBase.put("tp_atr_mult", 0.0); // reset

        // ── B: FLIP ENTRY + same risk management ──────────────────
        // More trades = statistically more consistent results per stock
        Map<String, Object> flipBase = new HashMap<>();
        flipBase.put("entry_mode", "flip");
        flipBase.put("ha_flip_exit", false);
        flipBase.put("tp_atr_mult", 0.0);
        flipBase.put("trailing_stop", true);
        flipBase.put("confirm_bars", 2);
        flipBase.put("sl_atr_mult", 8.0);
        flipBase.put("trail_atr_mult", 6.0);
        flipBase.put("trail_activation_pct", 0.25);
        flipBase.put("cooldown_bars", 18);
        flipBase.put("max_consec_losses", 4);

        ha(s, "HA_R30", "FLIP_BASE", flipBase);

        // Flip + TP sweep
        for (double tp : new double[]{5.0, 8.0, 10.0, 12.0, 15.0, 20.0, 30.0, 50.0}) {
            ha(s, "HA_R30", String.format("FLIP_TP%.0f", tp), flipBase, "tp_atr_mult", tp);
        }

        // Flip + tighter SL/TR (reduce per-trade variance)
        double[][] tightPairs = {{3, 3}, {3, 4}, {4, 4}, {4, 5}, {5, 5}, {5, 6}, {6, 6}};
        for (double[] p : tightPairs) {
            ha(s, "HA_R30", String.format("FLIP_SL%.0f_TR%.0f", p[0], p[1]), flipBase,
                    "sl_atr_mult", p[0], "trail_atr_mult", p[1]);
        }

        // Flip + tighter SL/TR + TP
        for (double tp : new double[]{8.0, 10.0, 15.0}) {
            ha(s, "HA_R30", String.format("FLIP_SL5_TR5_TP%.0f", tp), flipBase,
                    "sl_atr_mult", 5.0, "trail_atr_mult", 5.0, "tp_atr_mult", tp);
            ha(s, "HA_R30", String.format("FLIP_SL6_TR6_TP%.0f", tp), flipBase,
                    "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "tp_atr_mult", tp);
        }

        // Flip + CD sweep
        for (int cd : new int[]{10, 15, 20, 25, 30}) {
            ha(s, "HA_R30", "FLIP_CD" + cd, flipBase, "cooldown_bars", cd);
        }

        // Flip + L sweep
        for (int l : new int[]{2, 3, 4, 5}) {
            ha(s, "HA_R30", "FLIP_L" + l, flipBase, "max_consec_losses", l);
        }

        // ── C: REGIME + tighter SL/TR ─────────────────────────────
        for (double[] p : tightPairs) {
            ha(s, "HA_R30", String.format("REG_SL%.0f_TR%.0f", p[0], p[1]), regBase,
                    "sl_atr_mult", p[0], "trail_atr_mult", p[1]);
        }

        // Regime + tighter + TP
        for (double tp : new double[]{8.0, 10.0, 15.0}) {
            ha(s, "HA_R30", String.format("REG_SL5_TR5_TP%.0f", tp), regBase,
                    "sl_atr_mult", 5.0, "trail_atr_mult", 5.0, "tp_atr_mult", tp);
            ha(s, "HA_R30", String.format("REG_SL6_TR6_TP%.0f", tp), regBase,
                    "sl_atr_mult", 6.0, "trail_atr_mult", 6.0, "tp_atr_mult", tp);
        }

        // ── D: FLIP + very wide SL + TP (best of both worlds) ────
        // Wide SL preserves winners, TP caps extremes
        for (double tp : new double[]{8.0, 10.0, 15.0, 20.0}) {
            ha(s, "HA_R30", String.format("FLIP_SL8_TR6_TP%.0f", tp), flipBase,
                    "sl_atr_mult", 8.0, "trail_atr_mult", 6.0, "tp_atr_mult", tp);
            ha(s, "HA_R30", String.format("FLIP_SL10_TR6_TP%.0f", tp), flipBase,
                    "sl_atr_mult", 10.0, "trail_atr_mult", 6.0, "tp_atr_mult", tp);
        }

        // ── E: FLIP + TP + CD + L combos ─────────────────────────
        ha(s, "HA_R30", "FLIP_TP10_CD20_L4", flipBase,
                "tp_atr_mult", 10.0, "cooldown_bars", 20, "max_consec_losses", 4);
        ha(s, "HA_R30", "FLIP_TP15_CD20_L4", flipBase,
                "tp_atr_mult", 15.0, "cooldown_bars", 20, "max_consec_losses", 4);
        ha(s, "HA_R30", "FLIP_TP10_CD25_L3", flipBase,
                "tp_atr_mult", 10.0, "cooldown_bars", 25, "max_consec_losses", 3);
        ha(s, "HA_R30", "FLIP_TP15_CD25_L3", flipBase,
                "tp_atr_mult", 15.0, "cooldown_bars", 25, "max_consec_losses", 3);

        // ── F: FLIP with A2 (helped in R28, 79.3) ────────────────
        ha(s, "HA_R30", "FLIP_A2", flipBase, "trail_activation_pct", 2.0);
        ha(s, "HA_R30", "FLIP_A2_TP10", flipBase,
                "trail_activation_pct", 2.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R30", "FLIP_A2_TP15", flipBase,
                "trail_activation_pct", 2.0, "tp_atr_mult", 15.0);

        // ── G: Exit EMA to cap extreme winners ────────────────────
        ha(s, "HA_R30", "FLIP_XEMA20", flipBase, "exit_ema", 20);
        ha(s, "HA_R30", "FLIP_XEMA50", flipBase, "exit_ema", 50);
        ha(s, "HA_R30", "REG_XEMA20", regBase, "exit_ema", 20);
        ha(s, "HA_R30", "REG_XEMA50", regBase, "exit_ema", 50);

        // ── H: Regime + high RSI threshold (be more selective) ────
        // Higher RSI entry = only buy deeper dips = more selective
        ha(s, "HA_R30", "REG_RSI20_TP10", regBase,
                "rsi_entry", 20.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R30", "REG_RSI15_TP10", regBase,
                "rsi_entry", 15.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R30", "REG_RSI10_TP10", regBase,
                "rsi_entry", 10.0, "tp_atr_mult", 10.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R31: MAXIMIZE CALMAR → push to 95+
    //  Need avgCalmar ≥ 0.475 for CalmarScore 95.
    //  Currently 0.434 (86.7). Focus: boost CAGR/MDD ratio.
    //  Also: extreme regime params for more/better entries.
    // ══════════════════════════════════════════════════════════════
    private static void addR31Push(Map<String, Supplier<Strategy>> s) {

        // ── A: ULTRA-WIDE SL (no SL essentially) ─────────────────
        // Let trades run without premature stop-outs
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 4);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 30.0);
        base.put("regime_window", 9);
        base.put("regime_threshold", 0.4);
        base.put("tp_atr_mult", 0.0);

        // Extreme SL values
        for (double sl : new double[]{8, 10, 12, 15, 20, 30, 50, 0}) {
            ha(s, "HA_R31", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── B: Very low regime threshold (more entries) ───────────
        for (double t : new double[]{0.2, 0.25, 0.3, 0.35, 0.4}) {
            for (int w : new int[]{5, 7, 9}) {
                ha(s, "HA_R31", String.format("W%d_T%.2f", w, t), base,
                        "sl_atr_mult", 8.0, "regime_window", w, "regime_threshold", t);
            }
        }

        // ── C: Very low RSI (extreme dip-buying) ─────────────────
        for (double rsi : new double[]{5.0, 10.0, 15.0, 20.0, 25.0, 30.0}) {
            ha(s, "HA_R31", String.format("RSI%.0f", rsi), base,
                    "sl_atr_mult", 8.0, "rsi_entry", rsi);
        }

        // ── D: RSI period variation (faster/slower RSI) ───────────
        for (int p : new int[]{2, 3, 5, 7}) {
            for (double rsi : new double[]{20.0, 30.0, 40.0}) {
                ha(s, "HA_R31", String.format("RSIP%d_RSI%.0f", p, rsi), base,
                        "sl_atr_mult", 8.0, "rsi_period", p, "rsi_entry", rsi);
            }
        }

        // ── E: Wider trailing (let winners run further) ───────────
        for (double tr : new double[]{7, 8, 10, 12, 15}) {
            ha(s, "HA_R31", String.format("TR%.0f", tr), base,
                    "sl_atr_mult", 8.0, "trail_atr_mult", tr);
        }

        // ── F: Higher activation (only trail after big gains) ─────
        for (double a : new double[]{3.0, 5.0, 7.0, 10.0}) {
            ha(s, "HA_R31", String.format("SL8_A%.0f", a), base,
                    "sl_atr_mult", 8.0, "trail_activation_pct", a);
        }

        // ── G: Longer CD + wider L (patience) ────────────────────
        for (int cd : new int[]{20, 25, 30, 40}) {
            ha(s, "HA_R31", "SL8_CD" + cd, base, "sl_atr_mult", 8.0, "cooldown_bars", cd);
        }
        for (int l : new int[]{5, 6, 8, 10}) {
            ha(s, "HA_R31", "SL8_L" + l, base, "sl_atr_mult", 8.0, "max_consec_losses", l);
        }

        // ── H: Best combos targeting max Calmar ───────────────────
        // W5_T0.2 (very loose threshold) + SL0 (no stop loss)
        ha(s, "HA_R31", "W5_T0.2_SL0", base,
                "regime_window", 5, "regime_threshold", 0.2, "sl_atr_mult", 0.0);
        ha(s, "HA_R31", "W5_T0.25_SL20", base,
                "regime_window", 5, "regime_threshold", 0.25, "sl_atr_mult", 20.0);
        ha(s, "HA_R31", "W7_T0.3_SL15", base,
                "regime_window", 7, "regime_threshold", 0.3, "sl_atr_mult", 15.0);

        // RSI10 + SL15 (extreme dip buy + no premature exit)
        ha(s, "HA_R31", "RSI10_SL15", base,
                "rsi_entry", 10.0, "sl_atr_mult", 15.0);
        ha(s, "HA_R31", "RSI15_SL15", base,
                "rsi_entry", 15.0, "sl_atr_mult", 15.0);
        ha(s, "HA_R31", "RSI20_SL15", base,
                "rsi_entry", 20.0, "sl_atr_mult", 15.0);

        // ── I: Moderate TP to help both pillars ───────────────────
        // TP10 was the best compromise in R30 (robust 75.1)
        // Try TP10 with max-Calmar configs
        ha(s, "HA_R31", "SL15_TP10", base, "sl_atr_mult", 15.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R31", "SL20_TP10", base, "sl_atr_mult", 20.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R31", "SL15_TP12", base, "sl_atr_mult", 15.0, "tp_atr_mult", 12.0);
        ha(s, "HA_R31", "RSI20_SL15_TP10", base,
                "rsi_entry", 20.0, "sl_atr_mult", 15.0, "tp_atr_mult", 10.0);
        ha(s, "HA_R31", "W7_T0.3_SL15_TP10", base,
                "regime_window", 7, "regime_threshold", 0.3,
                "sl_atr_mult", 15.0, "tp_atr_mult", 10.0);

        // ── J: SL/TR/A sweep on best R28 config ──────────────────
        // W9_SL8_L4 was 80.3, try to push Calmar higher
        ha(s, "HA_R31", "W9_SL10_L4", base,
                "regime_window", 9, "sl_atr_mult", 10.0, "max_consec_losses", 4);
        ha(s, "HA_R31", "W9_SL12_L4", base,
                "regime_window", 9, "sl_atr_mult", 12.0, "max_consec_losses", 4);
        ha(s, "HA_R31", "W9_SL15_L4", base,
                "regime_window", 9, "sl_atr_mult", 15.0, "max_consec_losses", 4);
        ha(s, "HA_R31", "W9_SL8_L4_TR8", base,
                "regime_window", 9, "sl_atr_mult", 8.0, "max_consec_losses", 4,
                "trail_atr_mult", 8.0);
        ha(s, "HA_R31", "W9_SL8_L4_A2", base,
                "regime_window", 9, "sl_atr_mult", 8.0, "max_consec_losses", 4,
                "trail_activation_pct", 2.0);
        ha(s, "HA_R31", "W9_SL10_L4_A2", base,
                "regime_window", 9, "sl_atr_mult", 10.0, "max_consec_losses", 4,
                "trail_activation_pct", 2.0);

        // ── K: TP10 on best R28 config ────────────────────────────
        ha(s, "HA_R31", "W9_SL8_L4_TP10", base,
                "regime_window", 9, "sl_atr_mult", 8.0, "max_consec_losses", 4,
                "tp_atr_mult", 10.0);
        ha(s, "HA_R31", "W9_SL10_L4_TP10", base,
                "regime_window", 9, "sl_atr_mult", 10.0, "max_consec_losses", 4,
                "tp_atr_mult", 10.0);
        ha(s, "HA_R31", "W9_SL12_L4_TP10", base,
                "regime_window", 9, "sl_atr_mult", 12.0, "max_consec_losses", 4,
                "tp_atr_mult", 10.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R32: FIND OPTIMAL TP SWEET SPOT
    //  Score = 0.5 × Calmar + 0.5 × Robust
    //  TP helps Robust (caps variance) but hurts Calmar (caps CAGR)
    //  Search for the exact crossover that maximizes total Score
    // ══════════════════════════════════════════════════════════════
    private static void addR32Push(Map<String, Supplier<Strategy>> s) {

        // W9_SL8_L4 base (the 80.3 winner)
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "regime");
        base.put("ha_flip_exit", false);
        base.put("trailing_stop", true);
        base.put("confirm_bars", 2);
        base.put("sl_atr_mult", 8.0);
        base.put("trail_atr_mult", 6.0);
        base.put("trail_activation_pct", 0.25);
        base.put("cooldown_bars", 18);
        base.put("max_consec_losses", 4);
        base.put("rsi_period", 2);
        base.put("rsi_entry", 30.0);
        base.put("regime_window", 9);
        base.put("regime_threshold", 0.4);

        // ── A: Dense TP sweep (find exact optimum) ────────────────
        for (int tp = 8; tp <= 30; tp += 1) {
            ha(s, "HA_R32", "TP" + tp, base, "tp_atr_mult", (double) tp);
        }
        for (double tp : new double[]{35, 40, 50, 75, 100, 150, 200}) {
            ha(s, "HA_R32", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── B: No TP baseline ─────────────────────────────────────
        ha(s, "HA_R32", "NO_TP", base);

        // ── C: TP + L combos ──────────────────────────────────────
        // Try different L values with best TP range
        for (int tp : new int[]{10, 12, 15}) {
            for (int l : new int[]{3, 4, 5, 6}) {
                ha(s, "HA_R32", String.format("TP%d_L%d", tp, l), base,
                        "tp_atr_mult", (double) tp, "max_consec_losses", l);
            }
        }

        // ── D: TP + CD combos ─────────────────────────────────────
        for (int tp : new int[]{10, 12, 15}) {
            for (int cd : new int[]{15, 20, 25}) {
                ha(s, "HA_R32", String.format("TP%d_CD%d", tp, cd), base,
                        "tp_atr_mult", (double) tp, "cooldown_bars", cd);
            }
        }

        // ── E: TP + A2 (activation 2% helped in R28) ─────────────
        for (int tp : new int[]{10, 12, 15, 20}) {
            ha(s, "HA_R32", String.format("TP%d_A2", tp), base,
                    "tp_atr_mult", (double) tp, "trail_activation_pct", 2.0);
        }

        // ── F: TP + SL variation ──────────────────────────────────
        for (int tp : new int[]{10, 12}) {
            for (double sl : new double[]{6, 8, 10, 12, 15}) {
                ha(s, "HA_R32", String.format("TP%d_SL%.0f", tp, sl), base,
                        "tp_atr_mult", (double) tp, "sl_atr_mult", sl);
            }
        }

        // ── G: TP + TR variation ──────────────────────────────────
        for (int tp : new int[]{10, 12}) {
            for (double tr : new double[]{4, 5, 6, 7, 8}) {
                ha(s, "HA_R32", String.format("TP%d_TR%.0f", tp, tr), base,
                        "tp_atr_mult", (double) tp, "trail_atr_mult", tr);
            }
        }

        // ── H: Multi-combo stacking with TP ───────────────────────
        ha(s, "HA_R32", "TP10_L5_CD20", base,
                "tp_atr_mult", 10.0, "max_consec_losses", 5, "cooldown_bars", 20);
        ha(s, "HA_R32", "TP10_A2_L5", base,
                "tp_atr_mult", 10.0, "trail_activation_pct", 2.0, "max_consec_losses", 5);
        ha(s, "HA_R32", "TP12_L5_CD20", base,
                "tp_atr_mult", 12.0, "max_consec_losses", 5, "cooldown_bars", 20);
        ha(s, "HA_R32", "TP10_SL10_L5", base,
                "tp_atr_mult", 10.0, "sl_atr_mult", 10.0, "max_consec_losses", 5);
        ha(s, "HA_R32", "TP10_SL12_L5", base,
                "tp_atr_mult", 10.0, "sl_atr_mult", 12.0, "max_consec_losses", 5);
    }

    // ══════════════════════════════════════════════════════════════
    //  BEST WINNERS — for all-stock validation
    // ══════════════════════════════════════════════════════════════
    private static void addBestWinners(Map<String, Supplier<Strategy>> s) {

        // #1 (80.6) — R32:TP22 — regime W9_T0.4_RSI30 + SL8 + TR6 + A0.25 + CD18 + L4 + TP22
        Map<String, Object> winner1 = new HashMap<>();
        winner1.put("entry_mode", "regime");
        winner1.put("ha_flip_exit", false);
        winner1.put("trailing_stop", true);
        winner1.put("confirm_bars", 2);
        winner1.put("sl_atr_mult", 8.0);
        winner1.put("trail_atr_mult", 6.0);
        winner1.put("trail_activation_pct", 0.25);
        winner1.put("cooldown_bars", 18);
        winner1.put("max_consec_losses", 4);
        winner1.put("rsi_period", 2);
        winner1.put("rsi_entry", 30.0);
        winner1.put("regime_window", 9);
        winner1.put("regime_threshold", 0.4);
        winner1.put("tp_atr_mult", 22.0);
        ha(s, "BEST", "R32_TP22", winner1);

        // #2 (80.3) — R28:W9_SL8_L4 — same but no TP
        ha(s, "BEST", "R28_W9_SL8_L4", winner1, "tp_atr_mult", 0.0);

        // #3 (80.5) — R29:TP100
        ha(s, "BEST", "R29_TP100", winner1, "tp_atr_mult", 100.0);

        // #4 (76.9) — R25:SL7_A0.25 (pre-regime, flip entry)
        Map<String, Object> flipWinner = new HashMap<>();
        flipWinner.put("entry_mode", "flip");
        flipWinner.put("ha_flip_exit", false);
        flipWinner.put("tp_atr_mult", 0.0);
        flipWinner.put("trailing_stop", true);
        flipWinner.put("confirm_bars", 2);
        flipWinner.put("sl_atr_mult", 7.0);
        flipWinner.put("trail_atr_mult", 6.0);
        flipWinner.put("trail_activation_pct", 0.25);
        flipWinner.put("cooldown_bars", 18);
        flipWinner.put("max_consec_losses", 3);
        flipWinner.put("rsi_period", 2);
        flipWinner.put("rsi_entry", 40.0);
        flipWinner.put("regime_window", 10);
        flipWinner.put("regime_threshold", 0.6);
        ha(s, "BEST", "R25_FLIP_SL7_A025", flipWinner);

        // #5 (74.6) — R18:CD15 (flip entry, simpler config)
        Map<String, Object> flipSimple = new HashMap<>();
        flipSimple.put("entry_mode", "flip");
        flipSimple.put("ha_flip_exit", false);
        flipSimple.put("tp_atr_mult", 0.0);
        flipSimple.put("trailing_stop", true);
        flipSimple.put("confirm_bars", 2);
        flipSimple.put("sl_atr_mult", 5.5);
        flipSimple.put("trail_atr_mult", 6.0);
        flipSimple.put("trail_activation_pct", 3.0);
        flipSimple.put("cooldown_bars", 15);
        flipSimple.put("max_consec_losses", 3);
        ha(s, "BEST", "R18_FLIP_CD15", flipSimple);

        // #6 (72.8) — R16:SL5.5_TR6.0 (the original breakthrough, no CD/L)
        Map<String, Object> original = new HashMap<>();
        original.put("entry_mode", "flip");
        original.put("ha_flip_exit", false);
        original.put("tp_atr_mult", 0.0);
        original.put("trailing_stop", true);
        original.put("confirm_bars", 2);
        original.put("sl_atr_mult", 5.5);
        original.put("trail_atr_mult", 6.0);
        original.put("trail_activation_pct", 3.0);
        ha(s, "BEST", "R16_FLIP_SL55_TR6", original);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK SWEEP — optimize for 2170 stocks
    //  Flip entry performed best (30.6). Explore broadly.
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockSweep(Map<String, Supplier<Strategy>> s) {

        // ── A: Flip entry base sweep ──────────────────────────────
        Map<String, Object> flip = new HashMap<>();
        flip.put("entry_mode", "flip");
        flip.put("ha_flip_exit", false);
        flip.put("tp_atr_mult", 0.0);
        flip.put("trailing_stop", true);
        flip.put("confirm_bars", 2);

        // SL × TR grid (key parameters)
        double[] sls = {1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0};
        double[] trs = {1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0};
        for (double sl : sls) {
            for (double tr : trs) {
                ha(s, "AS_F", String.format("SL%.1f_TR%.1f", sl, tr), flip,
                        "sl_atr_mult", sl, "trail_atr_mult", tr, "trail_activation_pct", 1.0);
            }
        }

        // ── B: HA flip exit ON (original best on all stocks) ──────
        Map<String, Object> haFlip = new HashMap<>();
        haFlip.put("entry_mode", "flip");
        haFlip.put("ha_flip_exit", true);
        haFlip.put("tp_atr_mult", 0.0);
        haFlip.put("trailing_stop", true);
        haFlip.put("sl_atr_mult", 0.0);

        double[] trails = {0.3, 0.4, 0.5, 0.6, 0.7, 1.0, 1.5, 2.0};
        double[] acts = {1.0, 2.0, 3.0, 5.0};
        for (double tr : trails) {
            for (double a : acts) {
                ha(s, "AS_HF", String.format("TR%.1f_A%.0f", tr, a), haFlip,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── C: Trend EMA filter (skip downtrending stocks) ────────
        // This should help massively on 2170 stocks — many are in downtrends
        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "AS_F", String.format("EMA%d_SL3_TR3", ema), flip,
                    "trend_ema", ema, "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0);
            ha(s, "AS_F", String.format("EMA%d_SL5_TR5", ema), flip,
                    "trend_ema", ema, "sl_atr_mult", 5.0, "trail_atr_mult", 5.0,
                    "trail_activation_pct", 1.0);
            ha(s, "AS_F", String.format("EMA%d_SL2_TR2", ema), flip,
                    "trend_ema", ema, "sl_atr_mult", 2.0, "trail_atr_mult", 2.0,
                    "trail_activation_pct", 1.0);
        }

        // ── D: Trend EMA + HA flip exit ───────────────────────────
        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "AS_HF", String.format("EMA%d_TR0.5_A3", ema), haFlip,
                    "trend_ema", ema, "trail_atr_mult", 0.5, "trail_activation_pct", 3.0);
            ha(s, "AS_HF", String.format("EMA%d_TR1_A2", ema), haFlip,
                    "trend_ema", ema, "trail_atr_mult", 1.0, "trail_activation_pct", 2.0);
        }

        // ── E: Cooldown + SL/TR ──────────────────────────────────
        for (int cd : new int[]{5, 10, 15, 20}) {
            ha(s, "AS_F", "SL3_TR3_CD" + cd, flip,
                    "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0, "cooldown_bars", cd);
        }

        // ── F: ADX filter (only trade trending stocks) ────────────
        for (int adx : new int[]{15, 20, 25}) {
            ha(s, "AS_F", "ADX" + adx + "_SL3_TR3", flip,
                    "adx_filter", true, "adx_threshold", (double) adx,
                    "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0);
        }

        // ── G: TP experiments ─────────────────────────────────────
        for (double tp : new double[]{3.0, 5.0, 8.0, 10.0}) {
            ha(s, "AS_F", String.format("SL3_TR3_TP%.0f", tp), flip,
                    "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0, "tp_atr_mult", tp);
        }

        // ── H: Tight SL (protect capital in bad stocks) ──────────
        for (double sl : new double[]{1.0, 1.5, 2.0}) {
            for (double tr : new double[]{1.0, 1.5, 2.0, 3.0}) {
                ha(s, "AS_F", String.format("TSL%.1f_TR%.1f", sl, tr), flip,
                        "sl_atr_mult", sl, "trail_atr_mult", tr,
                        "trail_activation_pct", 0.5);
            }
        }

        // ── I: EMA + SL + CD combo ───────────────────────────────
        ha(s, "AS_F", "EMA200_SL3_TR3_CD10", flip,
                "trend_ema", 200, "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                "trail_activation_pct", 1.0, "cooldown_bars", 10);
        ha(s, "AS_F", "EMA100_SL3_TR3_CD10", flip,
                "trend_ema", 100, "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                "trail_activation_pct", 1.0, "cooldown_bars", 10);
        ha(s, "AS_F", "EMA200_SL2_TR2_CD10", flip,
                "trend_ema", 200, "sl_atr_mult", 2.0, "trail_atr_mult", 2.0,
                "trail_activation_pct", 1.0, "cooldown_bars", 10);

        // ── J: Confirm bars variation ─────────────────────────────
        for (int cb : new int[]{0, 1, 3}) {
            ha(s, "AS_F", "SL3_TR3_CB" + cb, flip,
                    "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0, "confirm_bars", cb);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R2 — Fine-tune HA flip exit + tight trail (50.1)
    //  Winner: ha_flip_exit=true, TR0.5, A3, no SL
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR2(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", true);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trail_atr_mult", 0.5);
        base.put("trail_activation_pct", 3.0);

        ha(s, "AS2", "BASE", base);

        // ── A: Fine TR sweep ──────────────────────────────────────
        for (double tr : new double[]{0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.8}) {
            ha(s, "AS2", String.format("TR%.2f", tr), base, "trail_atr_mult", tr);
        }

        // ── B: Fine activation sweep ──────────────────────────────
        for (double a : new double[]{0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0}) {
            ha(s, "AS2", String.format("A%.1f", a), base, "trail_activation_pct", a);
        }

        // ── C: Add SL as extra protection ─────────────────────────
        for (double sl : new double[]{1.5, 2.0, 2.5, 3.0, 4.0, 5.0}) {
            ha(s, "AS2", String.format("SL%.1f", sl), base, "sl_atr_mult", sl);
        }

        // ── D: Cooldown ───────────────────────────────────────────
        for (int cd : new int[]{3, 5, 7, 10, 15, 20}) {
            ha(s, "AS2", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── E: Max consec losses ──────────────────────────────────
        for (int l : new int[]{2, 3, 4, 5}) {
            ha(s, "AS2", "L" + l, base, "max_consec_losses", l);
        }

        // ── F: Confirm bars ───────────────────────────────────────
        for (int cb : new int[]{0, 1, 2, 3}) {
            ha(s, "AS2", "CB" + cb, base, "confirm_bars", cb);
        }

        // ── G: ATR period ─────────────────────────────────────────
        for (int atr : new int[]{7, 10, 20}) {
            ha(s, "AS2", "ATR" + atr, base, "atr_period", atr);
        }

        // ── H: ADX filter ─────────────────────────────────────────
        for (int adx : new int[]{10, 15, 20, 25}) {
            ha(s, "AS2", "ADX" + adx, base, "adx_filter", true, "adx_threshold", (double) adx);
        }

        // ── I: Strong candle ──────────────────────────────────────
        ha(s, "AS2", "STRONG", base, "strong_candle", true);

        // ── J: Multi-bar HA exit ──────────────────────────────────
        for (int xb : new int[]{2, 3}) {
            ha(s, "AS2", "XB" + xb, base, "exit_bearish_bars", xb);
        }

        // ── K: TP experiments ─────────────────────────────────────
        for (double tp : new double[]{3.0, 5.0, 8.0, 10.0}) {
            ha(s, "AS2", String.format("TP%.0f", tp), base, "tp_atr_mult", tp);
        }

        // ── L: Combos of best individual mods ─────────────────────
        // SL + CD
        ha(s, "AS2", "SL2_CD5", base, "sl_atr_mult", 2.0, "cooldown_bars", 5);
        ha(s, "AS2", "SL2_CD10", base, "sl_atr_mult", 2.0, "cooldown_bars", 10);
        ha(s, "AS2", "SL3_CD5", base, "sl_atr_mult", 3.0, "cooldown_bars", 5);
        ha(s, "AS2", "SL3_CD10", base, "sl_atr_mult", 3.0, "cooldown_bars", 10);
        // SL + L
        ha(s, "AS2", "SL2_L3", base, "sl_atr_mult", 2.0, "max_consec_losses", 3);
        ha(s, "AS2", "SL3_L3", base, "sl_atr_mult", 3.0, "max_consec_losses", 3);
        // CD + L
        ha(s, "AS2", "CD5_L3", base, "cooldown_bars", 5, "max_consec_losses", 3);
        ha(s, "AS2", "CD10_L3", base, "cooldown_bars", 10, "max_consec_losses", 3);
        // SL + CD + L
        ha(s, "AS2", "SL2_CD5_L3", base, "sl_atr_mult", 2.0, "cooldown_bars", 5, "max_consec_losses", 3);
        ha(s, "AS2", "SL3_CD5_L3", base, "sl_atr_mult", 3.0, "cooldown_bars", 5, "max_consec_losses", 3);
        ha(s, "AS2", "SL2_CD10_L3", base, "sl_atr_mult", 2.0, "cooldown_bars", 10, "max_consec_losses", 3);

        // ── M: ATR20 (different volatility measure) ───────────────
        ha(s, "AS2", "ATR20_SL2", base, "atr_period", 20, "sl_atr_mult", 2.0);
        ha(s, "AS2", "ATR20_SL3", base, "atr_period", 20, "sl_atr_mult", 3.0);
        ha(s, "AS2", "ATR7_SL2", base, "atr_period", 7, "sl_atr_mult", 2.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R3 — Boost Robustness (38.7 → target 50+)
    //  pctProfitable=77.4%, consistency=0%
    //  Need: (1) more stocks profitable (2) reduce CAGR variance
    //  Strategy: EMA filter to skip downtrending stocks
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR3(Map<String, Supplier<Strategy>> s) {

        // ── A: HA flip exit + tight trail + EMA filter ────────────
        // EMA should filter out the 22.6% unprofitable stocks
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", true);
        base.put("tp_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trail_atr_mult", 0.5);
        base.put("trail_activation_pct", 3.0);

        // Baseline (no EMA)
        ha(s, "AS3", "NO_EMA", base);

        // EMA filter sweep
        for (int ema : new int[]{10, 20, 30, 50, 75, 100, 150, 200}) {
            ha(s, "AS3", "EMA" + ema, base, "trend_ema", ema);
        }

        // ── B: EMA + ATR20 (best from R2) ─────────────────────────
        for (int ema : new int[]{20, 50, 100, 200}) {
            ha(s, "AS3", "EMA" + ema + "_ATR20", base,
                    "trend_ema", ema, "atr_period", 20);
        }

        // ── C: EMA + SL protection ────────────────────────────────
        for (int ema : new int[]{50, 100, 200}) {
            for (double sl : new double[]{2.0, 3.0, 5.0}) {
                ha(s, "AS3", String.format("EMA%d_SL%.0f", ema, sl), base,
                        "trend_ema", ema, "sl_atr_mult", sl);
            }
        }

        // ── D: EMA + TR variation ─────────────────────────────────
        for (int ema : new int[]{50, 100, 200}) {
            for (double tr : new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 1.0}) {
                ha(s, "AS3", String.format("EMA%d_TR%.1f", ema, tr), base,
                        "trend_ema", ema, "trail_atr_mult", tr);
            }
        }

        // ── E: EMA + activation sweep ─────────────────────────────
        for (int ema : new int[]{50, 100}) {
            for (double a : new double[]{1.0, 2.0, 3.0, 5.0}) {
                ha(s, "AS3", String.format("EMA%d_A%.0f", ema, a), base,
                        "trend_ema", ema, "trail_activation_pct", a);
            }
        }

        // ── F: EMA slope filter (rising EMA required) ─────────────
        for (int ema : new int[]{50, 100}) {
            for (int slp : new int[]{3, 5, 10}) {
                ha(s, "AS3", String.format("EMA%d_SLP%d", ema, slp), base,
                        "trend_ema", ema, "ema_slope_bars", slp);
            }
        }

        // ── G: ADX filter (trending stocks only) ──────────────────
        for (int adx : new int[]{15, 20, 25, 30}) {
            ha(s, "AS3", "ADX" + adx, base, "adx_filter", true, "adx_threshold", (double) adx);
        }

        // ── H: ADX + EMA combo ────────────────────────────────────
        for (int ema : new int[]{50, 100}) {
            for (int adx : new int[]{15, 20, 25}) {
                ha(s, "AS3", String.format("EMA%d_ADX%d", ema, adx), base,
                        "trend_ema", ema, "adx_filter", true, "adx_threshold", (double) adx);
            }
        }

        // ── I: TP to cap outliers + EMA ───────────────────────────
        for (int ema : new int[]{50, 100}) {
            for (double tp : new double[]{3.0, 5.0, 8.0}) {
                ha(s, "AS3", String.format("EMA%d_TP%.0f", ema, tp), base,
                        "trend_ema", ema, "tp_atr_mult", tp);
            }
        }

        // ── J: Cooldown + EMA ─────────────────────────────────────
        for (int ema : new int[]{50, 100}) {
            for (int cd : new int[]{5, 10}) {
                ha(s, "AS3", String.format("EMA%d_CD%d", ema, cd), base,
                        "trend_ema", ema, "cooldown_bars", cd);
            }
        }

        // ── K: No HA flip exit + EMA (like curated approach) ──────
        Map<String, Object> noFlip = new HashMap<>();
        noFlip.put("entry_mode", "flip");
        noFlip.put("ha_flip_exit", false);
        noFlip.put("tp_atr_mult", 0.0);
        noFlip.put("trailing_stop", true);
        noFlip.put("confirm_bars", 2);

        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "AS3", String.format("NOHF_EMA%d_SL3_TR3", ema), noFlip,
                    "trend_ema", ema, "sl_atr_mult", 3.0, "trail_atr_mult", 3.0,
                    "trail_activation_pct", 1.0);
            ha(s, "AS3", String.format("NOHF_EMA%d_SL5_TR5", ema), noFlip,
                    "trend_ema", ema, "sl_atr_mult", 5.0, "trail_atr_mult", 5.0,
                    "trail_activation_pct", 1.0);
        }

        // ── L: SMC Lite with EMA filter (different strategy) ──────
        Map<String, Object> smclBase = new HashMap<>();
        smclBase.put("require_bos", false);
        smclBase.put("entry_mode", "ha_flip");
        smclBase.put("bos_entry", false);
        smclBase.put("ha_flip_exit", true);
        smclBase.put("sl_atr_mult", 0.0);
        smclBase.put("trailing_stop", true);
        smclBase.put("trail_atr_mult", 0.5);
        smclBase.put("trail_activation_pct", 3.0);

        for (int ema : new int[]{0, 50, 100, 200}) {
            smclBase.put("trend_ema", ema);
            smcl(s, "AS3_SMCL", "EMA" + ema, new HashMap<>(smclBase));
        }
        // SMCL + BOS exit
        smclBase.put("trend_ema", 0);
        smclBase.put("bos_exit", true);
        smcl(s, "AS3_SMCL", "BOSX", new HashMap<>(smclBase));
        smclBase.put("st_flip_exit", true);
        smclBase.put("atr_period", 20);
        smcl(s, "AS3_SMCL", "DUAL7", new HashMap<>(smclBase), "st_period", 7);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R4 — Deep dive on SMCL DUAL7 (50.6)
    //  and HA flip exit with tight trailing
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR4(Map<String, Supplier<Strategy>> s) {

        // ── A: SMCL + Dual exit sweep ─────────────────────────────
        Map<String, Object> smclDual = new HashMap<>();
        smclDual.put("require_bos", false);
        smclDual.put("entry_mode", "ha_flip");
        smclDual.put("bos_entry", false);
        smclDual.put("ha_flip_exit", true);
        smclDual.put("sl_atr_mult", 0.0);
        smclDual.put("trailing_stop", true);
        smclDual.put("trail_atr_mult", 0.5);
        smclDual.put("trail_activation_pct", 3.0);
        smclDual.put("st_flip_exit", true);

        // ST period × factor sweep
        int[] stPeriods = {5, 7, 10, 14, 20};
        double[] stFactors = {1.5, 2.0, 2.5, 3.0, 4.0};
        for (int p : stPeriods) {
            for (double f : stFactors) {
                smcl(s, "AS4", String.format("ST%d_F%.1f", p, f), smclDual,
                        "st_period", p, "st_factor", f, "atr_period", 20);
            }
        }

        // ── B: SMCL dual + ATR period ─────────────────────────────
        for (int atr : new int[]{7, 10, 14, 20}) {
            smcl(s, "AS4", "ST7_ATR" + atr, smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", atr);
        }

        // ── C: SMCL dual + TR sweep ──────────────────────────────
        for (double tr : new double[]{0.3, 0.4, 0.45, 0.5, 0.55, 0.6, 0.7}) {
            smcl(s, "AS4", String.format("ST7_TR%.2f", tr), smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", 20,
                    "trail_atr_mult", tr);
        }

        // ── D: SMCL dual + activation sweep ──────────────────────
        for (double a : new double[]{1.0, 2.0, 2.5, 3.0, 4.0, 5.0}) {
            smcl(s, "AS4", String.format("ST7_A%.1f", a), smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", 20,
                    "trail_activation_pct", a);
        }

        // ── E: SMCL dual + SL ─────────────────────────────────────
        for (double sl : new double[]{1.5, 2.0, 3.0, 4.0, 5.0}) {
            smcl(s, "AS4", String.format("ST7_SL%.1f", sl), smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", 20,
                    "sl_atr_mult", sl);
        }

        // ── F: SMCL dual + BOS exit ──────────────────────────────
        smcl(s, "AS4", "ST7_BOSX", smclDual,
                "st_period", 7, "st_factor", 3.0, "atr_period", 20, "bos_exit", true);
        // Different swing params for BOS
        smcl(s, "AS4", "ST7_BOSX_SW20", smclDual,
                "st_period", 7, "st_factor", 3.0, "atr_period", 20, "bos_exit", true,
                "swing_left", 20, "swing_right", 3);

        // ── G: SMCL dual + TP ─────────────────────────────────────
        for (double tp : new double[]{3.0, 5.0, 8.0}) {
            smcl(s, "AS4", String.format("ST7_TP%.0f", tp), smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", 20,
                    "tp_atr_mult", tp);
        }

        // ── H: SMCL dual + cooldown ──────────────────────────────
        for (int cd : new int[]{3, 5, 10}) {
            smcl(s, "AS4", "ST7_CD" + cd, smclDual,
                    "st_period", 7, "st_factor", 3.0, "atr_period", 20,
                    "cooldown_bars", cd);
        }

        // ── I: HA flip exit + ST trend filter (different approach) ─
        Map<String, Object> haStf = new HashMap<>();
        haStf.put("entry_mode", "flip");
        haStf.put("ha_flip_exit", true);
        haStf.put("tp_atr_mult", 0.0);
        haStf.put("trailing_stop", true);
        haStf.put("sl_atr_mult", 0.0);
        haStf.put("trail_atr_mult", 0.5);
        haStf.put("trail_activation_pct", 3.0);

        // Supertrend as entry filter (not exit)
        // Only enter when Supertrend is bullish
        // This is different from st_flip_exit which uses ST as exit

        // ── J: Pure HA + different ATR periods ────────────────────
        for (int atr : new int[]{5, 7, 10, 14, 20, 30}) {
            ha(s, "AS4_HA", "ATR" + atr, haStf, "atr_period", atr);
        }

        // ── K: HA + different TR × A combos (fine grid) ──────────
        double[] trs = {0.35, 0.4, 0.45, 0.5, 0.55, 0.6};
        double[] acts = {1.5, 2.0, 2.5, 3.0, 3.5, 4.0};
        for (double tr : trs) {
            for (double a : acts) {
                ha(s, "AS4_HA", String.format("TR%.2f_A%.1f", tr, a), haStf,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── L: HA + ATR20 + TR/A sweep ────────────────────────────
        for (double tr : new double[]{0.4, 0.45, 0.5, 0.55}) {
            for (double a : new double[]{2.0, 2.5, 3.0, 3.5}) {
                ha(s, "AS4_HA", String.format("ATR20_TR%.2f_A%.1f", tr, a), haStf,
                        "atr_period", 20, "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R5: Compress CAGR variance + cut worst losers
    //  Goal: boost pctProfitable above 77.4%, improve consistency from 0%
    //  Approach: tight TP caps winners, SL cuts losers → narrows CAGR spread
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR5(Map<String, Supplier<Strategy>> s) {

        // ── Base: SMCL DUAL7 winner (score 50.6) ─────────────────
        Map<String, Object> dual7 = new HashMap<>();
        dual7.put("require_bos", false);
        dual7.put("entry_mode", "ha_flip");
        dual7.put("bos_entry", false);
        dual7.put("ha_flip_exit", true);
        dual7.put("sl_atr_mult", 0.0);
        dual7.put("trailing_stop", true);
        dual7.put("trail_atr_mult", 0.5);
        dual7.put("trail_activation_pct", 3.0);
        dual7.put("st_flip_exit", true);
        dual7.put("st_period", 7);
        dual7.put("st_factor", 3.0);
        dual7.put("atr_period", 20);

        // ── A: Tight TP to compress CAGR variance ────────────────
        // TP caps max gain per trade → reduces CAGR spread → improves consistency
        for (double tp : new double[]{1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0, 8.0, 10.0}) {
            smcl(s, "AS5", String.format("TP%.1f", tp), dual7, "tp_atr_mult", tp);
        }

        // ── B: SL to cut worst losers → boost pctProfitable ─────
        // Even modest SL should prevent catastrophic losses on bad stocks
        for (double sl : new double[]{1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0}) {
            smcl(s, "AS5", String.format("SL%.1f", sl), dual7, "sl_atr_mult", sl);
        }

        // ── C: TP + SL combos (the key sweep) ───────────────────
        // Tight TP compresses winners, SL cuts losers → both improve consistency
        double[] tps = {2.0, 2.5, 3.0, 3.5, 4.0, 5.0};
        double[] sls = {1.0, 1.5, 2.0, 2.5, 3.0};
        for (double tp : tps) {
            for (double sl : sls) {
                smcl(s, "AS5", String.format("TP%.1f_SL%.1f", tp, sl), dual7,
                        "tp_atr_mult", tp, "sl_atr_mult", sl);
            }
        }

        // ── D: TP + SL + tighter trailing ────────────────────────
        // Tighter trail catches reversals faster on all stocks
        double[] trails = {0.3, 0.4, 0.5, 0.7};
        for (double tr : trails) {
            for (double tp : new double[]{2.5, 3.0, 4.0}) {
                for (double sl : new double[]{1.5, 2.0, 3.0}) {
                    smcl(s, "AS5", String.format("TP%.1f_SL%.1f_TR%.1f", tp, sl, tr), dual7,
                            "tp_atr_mult", tp, "sl_atr_mult", sl, "trail_atr_mult", tr);
                }
            }
        }

        // ── E: TP + SL + lower activation ────────────────────────
        // Lower activation = trail kicks in sooner = protects gains earlier
        for (double a : new double[]{0.5, 1.0, 1.5, 2.0}) {
            for (double tp : new double[]{2.5, 3.0, 4.0}) {
                smcl(s, "AS5", String.format("TP%.1f_A%.1f", tp, a), dual7,
                        "tp_atr_mult", tp, "trail_activation_pct", a);
            }
        }

        // ── F: TP + SL + activation combos ───────────────────────
        for (double tp : new double[]{2.5, 3.0, 3.5}) {
            for (double sl : new double[]{1.5, 2.0}) {
                for (double a : new double[]{1.0, 1.5, 2.0}) {
                    smcl(s, "AS5", String.format("TP%.1f_SL%.1f_A%.1f", tp, sl, a), dual7,
                            "tp_atr_mult", tp, "sl_atr_mult", sl, "trail_activation_pct", a);
                }
            }
        }

        // ── G: No ST flip exit (pure HA + trail + TP/SL) ────────
        // ST flip exit might be too aggressive on some stocks
        Map<String, Object> noSt = new HashMap<>(dual7);
        noSt.put("st_flip_exit", false);
        for (double tp : new double[]{2.5, 3.0, 4.0, 5.0}) {
            for (double sl : new double[]{1.5, 2.0, 3.0}) {
                smcl(s, "AS5", String.format("NOST_TP%.1f_SL%.1f", tp, sl), noSt,
                        "tp_atr_mult", tp, "sl_atr_mult", sl);
            }
        }

        // ── H: HA strategy (not SMCL) with TP/SL ────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);

        for (double tp : new double[]{2.0, 2.5, 3.0, 4.0, 5.0}) {
            for (double sl : new double[]{0.0, 1.5, 2.0, 3.0}) {
                ha(s, "AS5_HA", String.format("TP%.1f_SL%.1f", tp, sl), haBase,
                        "tp_atr_mult", tp, "sl_atr_mult", sl, "atr_period", 20);
            }
        }

        // ── I: Cooldown to reduce overtrading on noisy stocks ────
        for (int cd : new int[]{2, 3, 5, 8}) {
            smcl(s, "AS5", "CD" + cd, dual7, "cooldown_bars", cd);
            // CD + best TP/SL
            smcl(s, "AS5", String.format("TP3.0_SL2.0_CD%d", cd), dual7,
                    "tp_atr_mult", 3.0, "sl_atr_mult", 2.0, "cooldown_bars", cd);
        }

        // ── J: Max consecutive losses to stop bleeding ───────────
        for (int mcl : new int[]{2, 3, 4, 5}) {
            smcl(s, "AS5", "MCL" + mcl, dual7, "max_consec_losses", mcl);
            smcl(s, "AS5", String.format("TP3.0_SL2.0_MCL%d", mcl), dual7,
                    "tp_atr_mult", 3.0, "sl_atr_mult", 2.0, "max_consec_losses", mcl);
        }

        // ── K: Baseline for comparison ───────────────────────────
        smcl(s, "AS5", "DUAL7_BASE", dual7);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R6: Fix DUAL7 baseline (was missing bos_exit=true)
    //  + Build on HA+TP approach (best R5 scorer at 46.2)
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR6(Map<String, Supplier<Strategy>> s) {

        // ── A: Fix DUAL7 — add bos_exit=true (the original had it) ──
        Map<String, Object> dual7fix = new HashMap<>();
        dual7fix.put("require_bos", false);
        dual7fix.put("entry_mode", "ha_flip");
        dual7fix.put("bos_entry", false);
        dual7fix.put("ha_flip_exit", true);
        dual7fix.put("sl_atr_mult", 0.0);
        dual7fix.put("trailing_stop", true);
        dual7fix.put("trail_atr_mult", 0.5);
        dual7fix.put("trail_activation_pct", 3.0);
        dual7fix.put("st_flip_exit", true);
        dual7fix.put("st_period", 7);
        dual7fix.put("st_factor", 3.0);
        dual7fix.put("atr_period", 20);
        dual7fix.put("bos_exit", true);

        smcl(s, "AS6", "DUAL7_BOSX", dual7fix);
        // Variants on DUAL7+bos_exit
        for (double tp : new double[]{3.0, 4.0, 5.0, 6.0, 8.0}) {
            smcl(s, "AS6", String.format("DUAL7_BOSX_TP%.0f", tp), dual7fix, "tp_atr_mult", tp);
        }
        // Different swing params for BOS
        for (int sw : new int[]{5, 10, 15, 20}) {
            smcl(s, "AS6", "DUAL7_BOSX_SW" + sw, dual7fix, "swing_left", sw, "swing_right", 3);
        }

        // ── B: HA + TP sweep (best R5 approach) ─────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);

        // TP sweep (TP5 was best at 46.2, try higher)
        for (double tp : new double[]{5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 15.0, 20.0}) {
            ha(s, "AS6_HA", String.format("TP%.0f", tp), haBase, "tp_atr_mult", tp);
        }
        // No TP baseline
        ha(s, "AS6_HA", "NOTP", haBase);

        // ── C: HA + TP + different TR/A combos ──────────────────
        for (double tr : new double[]{0.3, 0.4, 0.5, 0.7, 1.0}) {
            ha(s, "AS6_HA", String.format("TP5_TR%.1f", tr), haBase, "tp_atr_mult", 5.0, "trail_atr_mult", tr);
            ha(s, "AS6_HA", String.format("TP7_TR%.1f", tr), haBase, "tp_atr_mult", 7.0, "trail_atr_mult", tr);
            ha(s, "AS6_HA", String.format("TP10_TR%.1f", tr), haBase, "tp_atr_mult", 10.0, "trail_atr_mult", tr);
        }

        // ── D: HA + TP + activation sweep ───────────────────────
        for (double a : new double[]{1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0}) {
            ha(s, "AS6_HA", String.format("TP5_A%.1f", a), haBase, "tp_atr_mult", 5.0, "trail_activation_pct", a);
            ha(s, "AS6_HA", String.format("TP7_A%.1f", a), haBase, "tp_atr_mult", 7.0, "trail_activation_pct", a);
        }

        // ── E: HA + TP + TR + A combos ──────────────────────────
        for (double tr : new double[]{0.3, 0.5, 0.7}) {
            for (double a : new double[]{1.5, 2.0, 3.0, 4.0}) {
                ha(s, "AS6_HA", String.format("TP5_TR%.1f_A%.1f", tr, a), haBase,
                        "tp_atr_mult", 5.0, "trail_atr_mult", tr, "trail_activation_pct", a);
                ha(s, "AS6_HA", String.format("TP7_TR%.1f_A%.1f", tr, a), haBase,
                        "tp_atr_mult", 7.0, "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── F: HA + TP + different ATR periods ──────────────────
        for (int atr : new int[]{7, 10, 14, 20, 30}) {
            ha(s, "AS6_HA", String.format("TP5_ATR%d", atr), haBase, "tp_atr_mult", 5.0, "atr_period", atr);
            ha(s, "AS6_HA", String.format("TP7_ATR%d", atr), haBase, "tp_atr_mult", 7.0, "atr_period", atr);
        }

        // ── G: HA + ha_flip_exit=false + trailing (52-stock approach) ─
        // On 52 stocks, ha_flip_exit=false was the winner.
        // Test if it works on 2170 stocks with TP cap for variance control
        Map<String, Object> haNoFlip = new HashMap<>();
        haNoFlip.put("entry_mode", "flip");
        haNoFlip.put("ha_flip_exit", false);
        haNoFlip.put("trailing_stop", true);
        haNoFlip.put("trail_atr_mult", 6.0);
        haNoFlip.put("trail_activation_pct", 3.0);
        haNoFlip.put("sl_atr_mult", 4.0);
        haNoFlip.put("tp_atr_mult", 0.0);
        haNoFlip.put("confirm_bars", 2);

        ha(s, "AS6_HA", "NOFLIP_BASE", haNoFlip);
        for (double tp : new double[]{5.0, 8.0, 10.0, 15.0, 20.0, 30.0}) {
            ha(s, "AS6_HA", String.format("NOFLIP_TP%.0f", tp), haNoFlip, "tp_atr_mult", tp);
        }
        // NoFlip with different SL
        for (double sl : new double[]{2.0, 3.0, 4.0, 6.0, 8.0}) {
            ha(s, "AS6_HA", String.format("NOFLIP_SL%.0f", sl), haNoFlip, "sl_atr_mult", sl);
        }
        // NoFlip with TP + SL combos
        for (double tp : new double[]{10.0, 15.0, 20.0}) {
            for (double sl : new double[]{3.0, 4.0, 6.0}) {
                ha(s, "AS6_HA", String.format("NOFLIP_TP%.0f_SL%.0f", tp, sl), haNoFlip,
                        "tp_atr_mult", tp, "sl_atr_mult", sl);
            }
        }

        // ── H: HA + no flip + different TR ──────────────────────
        for (double tr : new double[]{3.0, 4.0, 5.0, 6.0, 8.0, 10.0}) {
            ha(s, "AS6_HA", String.format("NOFLIP_TR%.0f", tr), haNoFlip, "trail_atr_mult", tr);
        }

        // ── I: SMCL + bos_exit + different approach ─────────────
        // SMCL with bos_exit but no st_flip_exit
        Map<String, Object> smclBos = new HashMap<>();
        smclBos.put("require_bos", false);
        smclBos.put("entry_mode", "ha_flip");
        smclBos.put("bos_entry", false);
        smclBos.put("ha_flip_exit", true);
        smclBos.put("sl_atr_mult", 0.0);
        smclBos.put("trailing_stop", true);
        smclBos.put("trail_atr_mult", 0.5);
        smclBos.put("trail_activation_pct", 3.0);
        smclBos.put("atr_period", 20);
        smclBos.put("bos_exit", true);

        smcl(s, "AS6", "BOS_ONLY", smclBos);
        // BOS with different swing params
        for (int sw : new int[]{3, 5, 7, 10, 15, 20}) {
            smcl(s, "AS6", "BOS_SW" + sw, smclBos, "swing_left", sw, "swing_right", 3);
        }
        // BOS + ST flip exit
        for (int sw : new int[]{5, 7, 10}) {
            smcl(s, "AS6", "BOS_ST_SW" + sw, smclBos,
                    "st_flip_exit", true, "st_period", 7, "st_factor", 3.0,
                    "swing_left", sw, "swing_right", 3);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R7: New exit paradigm — exit_bearish_bars instead of ha_flip_exit
    //  ha_flip_exit exits on EVERY bearish bar, overriding TP/SL.
    //  exit_bearish_bars=N waits for N consecutive bearish bars → holds through pullbacks.
    //  Also: pullback entry, confirm_bars, TP/SL can now work properly.
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR7(Map<String, Supplier<Strategy>> s) {

        // ── Control: HA flip exit baseline (50.3 target) ─────────
        Map<String, Object> ctrl = new HashMap<>();
        ctrl.put("entry_mode", "flip");
        ctrl.put("ha_flip_exit", true);
        ctrl.put("trailing_stop", true);
        ctrl.put("trail_atr_mult", 0.5);
        ctrl.put("trail_activation_pct", 3.0);
        ctrl.put("sl_atr_mult", 0.0);
        ctrl.put("tp_atr_mult", 0.0);
        ctrl.put("atr_period", 20);
        ha(s, "AS7", "CTRL", ctrl);

        // ── A: exit_bearish_bars sweep (hold through brief pullbacks) ─
        // ha_flip_exit=false, use exit_bearish_bars=N for exit
        Map<String, Object> ebBase = new HashMap<>();
        ebBase.put("entry_mode", "flip");
        ebBase.put("ha_flip_exit", false);
        ebBase.put("trailing_stop", true);
        ebBase.put("trail_atr_mult", 3.0);
        ebBase.put("trail_activation_pct", 2.0);
        ebBase.put("sl_atr_mult", 0.0);
        ebBase.put("tp_atr_mult", 0.0);
        ebBase.put("atr_period", 20);

        for (int eb : new int[]{1, 2, 3, 4, 5}) {
            ha(s, "AS7", "EB" + eb, ebBase, "exit_bearish_bars", eb);
        }

        // ── B: exit_bearish_bars + trailing sweep ────────────────
        for (int eb : new int[]{2, 3}) {
            for (double tr : new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0}) {
                ha(s, "AS7", String.format("EB%d_TR%.0f", eb, tr), ebBase,
                        "exit_bearish_bars", eb, "trail_atr_mult", tr);
            }
        }

        // ── C: exit_bearish_bars + TP (now TP can actually work!) ─
        for (int eb : new int[]{2, 3}) {
            for (double tp : new double[]{3.0, 5.0, 8.0, 10.0, 15.0, 20.0}) {
                ha(s, "AS7", String.format("EB%d_TP%.0f", eb, tp), ebBase,
                        "exit_bearish_bars", eb, "tp_atr_mult", tp);
            }
        }

        // ── D: exit_bearish_bars + SL ────────────────────────────
        for (int eb : new int[]{2, 3}) {
            for (double sl : new double[]{2.0, 3.0, 4.0, 6.0, 8.0}) {
                ha(s, "AS7", String.format("EB%d_SL%.0f", eb, sl), ebBase,
                        "exit_bearish_bars", eb, "sl_atr_mult", sl);
            }
        }

        // ── E: exit_bearish_bars + TP + SL combos ───────────────
        for (int eb : new int[]{2, 3}) {
            for (double tp : new double[]{5.0, 8.0, 10.0, 15.0}) {
                for (double sl : new double[]{3.0, 4.0, 6.0}) {
                    ha(s, "AS7", String.format("EB%d_TP%.0f_SL%.0f", eb, tp, sl), ebBase,
                            "exit_bearish_bars", eb, "tp_atr_mult", tp, "sl_atr_mult", sl);
                }
            }
        }

        // ── F: confirm_bars for flip entry (filter weak signals) ─
        for (int cb : new int[]{2, 3}) {
            ha(s, "AS7", "CB" + cb, ctrl, "confirm_bars", cb);
            // CB + exit_bearish_bars
            for (int eb : new int[]{2, 3}) {
                ha(s, "AS7", String.format("CB%d_EB%d", cb, eb), ebBase,
                        "confirm_bars", cb, "exit_bearish_bars", eb);
            }
        }

        // ── G: Pullback entry mode ───────────────────────────────
        // Enter only after confirmed uptrend + pullback + resumption
        Map<String, Object> pbBase = new HashMap<>();
        pbBase.put("entry_mode", "pullback");
        pbBase.put("ha_flip_exit", true);
        pbBase.put("trailing_stop", true);
        pbBase.put("trail_atr_mult", 0.5);
        pbBase.put("trail_activation_pct", 3.0);
        pbBase.put("sl_atr_mult", 0.0);
        pbBase.put("tp_atr_mult", 0.0);
        pbBase.put("atr_period", 20);

        // Sweep min_trend_bars and max_pullback_bars
        for (int mt : new int[]{3, 5, 7, 10}) {
            for (int mp : new int[]{1, 2, 3}) {
                ha(s, "AS7", String.format("PB_MT%d_MP%d", mt, mp), pbBase,
                        "min_trend_bars", mt, "max_pullback_bars", mp);
            }
        }

        // Pullback with exit_bearish_bars (not ha_flip_exit)
        Map<String, Object> pbEb = new HashMap<>(pbBase);
        pbEb.put("ha_flip_exit", false);
        for (int mt : new int[]{3, 5, 7}) {
            for (int eb : new int[]{2, 3}) {
                ha(s, "AS7", String.format("PB_MT%d_EB%d", mt, eb), pbEb,
                        "min_trend_bars", mt, "max_pullback_bars", 2,
                        "exit_bearish_bars", eb, "trail_atr_mult", 3.0, "trail_activation_pct", 2.0);
            }
        }

        // ── H: Regime entry (worked on 52 stocks, test on all) ───
        Map<String, Object> regBase = new HashMap<>();
        regBase.put("entry_mode", "regime");
        regBase.put("ha_flip_exit", true);
        regBase.put("trailing_stop", true);
        regBase.put("trail_atr_mult", 0.5);
        regBase.put("trail_activation_pct", 3.0);
        regBase.put("sl_atr_mult", 0.0);
        regBase.put("tp_atr_mult", 0.0);
        regBase.put("atr_period", 20);
        regBase.put("rsi_period", 2);
        regBase.put("rsi_entry", 30.0);

        for (int rw : new int[]{7, 9, 12, 15}) {
            for (double rt : new double[]{0.3, 0.4, 0.5, 0.6}) {
                ha(s, "AS7", String.format("REG_W%d_T%.1f", rw, rt), regBase,
                        "regime_window", rw, "regime_threshold", rt);
            }
        }

        // ── I: RSI pullback entry mode ───────────────────────────
        Map<String, Object> rsiBase = new HashMap<>();
        rsiBase.put("entry_mode", "rsi_pullback");
        rsiBase.put("ha_flip_exit", true);
        rsiBase.put("trailing_stop", true);
        rsiBase.put("trail_atr_mult", 0.5);
        rsiBase.put("trail_activation_pct", 3.0);
        rsiBase.put("sl_atr_mult", 0.0);
        rsiBase.put("tp_atr_mult", 0.0);
        rsiBase.put("atr_period", 20);
        rsiBase.put("rsi_period", 2);

        for (double re : new double[]{20.0, 25.0, 30.0, 35.0, 40.0}) {
            for (int mt : new int[]{3, 5, 7}) {
                ha(s, "AS7", String.format("RSI%.0f_MT%d", re, mt), rsiBase,
                        "rsi_entry", re, "min_trend_bars", mt);
            }
        }

        // ── J: Volume filter — only trade on above-avg volume ────
        ha(s, "AS7", "VOL1.0", ctrl, "volume_filter", true, "vol_mult", 1.0, "vol_period", 20);
        ha(s, "AS7", "VOL1.2", ctrl, "volume_filter", true, "vol_mult", 1.2, "vol_period", 20);
        ha(s, "AS7", "VOL1.5", ctrl, "volume_filter", true, "vol_mult", 1.5, "vol_period", 20);

        // ── K: Trend EMA filter — only long above EMA ────────────
        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "AS7", "EMA" + ema, ctrl, "trend_ema", ema);
        }

        // ── L: ADX filter — only trade when trending ─────────────
        for (double adx : new double[]{15.0, 20.0, 25.0}) {
            ha(s, "AS7", String.format("ADX%.0f", adx), ctrl, "adx_filter", true, "adx_threshold", adx);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R8: Fixed SMCL config (trend_ema=0 was missing!)
    //  SMCL defaults trend_ema=200 which filters 50%+ of entries.
    //  Now: corrected SMCL base + sweep TP/SL/trail/exit combos
    //  Also: HA baseline for comparison + creative combos
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR8(Map<String, Supplier<Strategy>> s) {

        // ── Corrected SMCL DUAL7 base (trend_ema=0 is critical!) ──
        Map<String, Object> smcl7 = new HashMap<>();
        smcl7.put("require_bos", false);
        smcl7.put("trend_ema", 0);            // ← THE FIX: was defaulting to 200
        smcl7.put("entry_mode", "ha_flip");
        smcl7.put("bos_entry", false);
        smcl7.put("ha_flip_exit", true);
        smcl7.put("sl_atr_mult", 0.0);
        smcl7.put("trailing_stop", true);
        smcl7.put("trail_atr_mult", 0.5);
        smcl7.put("trail_activation_pct", 3.0);
        smcl7.put("st_flip_exit", true);
        smcl7.put("st_period", 7);
        smcl7.put("st_factor", 3.0);
        smcl7.put("atr_period", 20);

        // A1: Baseline without bos_exit
        smcl(s, "AS8", "DUAL7", smcl7);
        // A2: With bos_exit (original R3 had this)
        smcl(s, "AS8", "DUAL7_BOSX", smcl7, "bos_exit", true);

        // ── B: SMCL no ST exit (pure HA flip + BOS) ─────────────
        Map<String, Object> smclNoSt = new HashMap<>(smcl7);
        smclNoSt.put("st_flip_exit", false);
        smcl(s, "AS8", "NOSTE", smclNoSt);
        smcl(s, "AS8", "NOSTE_BOSX", smclNoSt, "bos_exit", true);

        // ── C: SMCL with different ST configs ───────────────────
        for (int p : new int[]{5, 7, 10, 14}) {
            for (double f : new double[]{2.0, 3.0, 4.0}) {
                smcl(s, "AS8", String.format("ST%d_F%.0f", p, f), smcl7,
                        "st_period", p, "st_factor", f);
            }
        }

        // ── D: SMCL DUAL7 + swing param sweep ──────────────────
        // Different swing structure params affect BOS detection
        for (int sw : new int[]{3, 5, 7, 10, 15, 20}) {
            smcl(s, "AS8", "SW" + sw, smcl7, "swing_left", sw, "swing_right", 2);
            smcl(s, "AS8", "BOSX_SW" + sw, smcl7, "bos_exit", true, "swing_left", sw, "swing_right", 2);
        }

        // ── E: SMCL with ha_exit_bars (delay HA exit) ───────────
        // ha_exit_bars>0 requires N consecutive bearish HA bars for ha exit
        for (int heb : new int[]{2, 3}) {
            smcl(s, "AS8", "HEB" + heb, smcl7, "ha_exit_bars", heb);
            smcl(s, "AS8", "HEB" + heb + "_BOSX", smcl7, "ha_exit_bars", heb, "bos_exit", true);
        }

        // ── F: SMCL trail/activation sweep ──────────────────────
        for (double tr : new double[]{0.3, 0.4, 0.5, 0.7, 1.0}) {
            for (double a : new double[]{1.0, 2.0, 3.0, 4.0}) {
                smcl(s, "AS8", String.format("TR%.1f_A%.0f", tr, a), smcl7,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── G: SMCL + cooldown ──────────────────────────────────
        for (int cd : new int[]{2, 3, 5}) {
            smcl(s, "AS8", "CD" + cd, smcl7, "cooldown_bars", cd);
        }

        // ── H: HA baseline (should be 50.3) ─────────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);
        ha(s, "AS8_HA", "BASE", haBase);

        // ── I: SMCL with different entry modes ──────────────────
        // Pullback mode within SMCL (BOS structure might help)
        smcl(s, "AS8", "PB", smcl7, "entry_mode", "pullback", "ha_flip_exit", true);
        smcl(s, "AS8", "RSI", smcl7, "entry_mode", "rsi", "ha_flip_exit", true, "rsi_period", 2, "rsi_threshold", 30.0);

        // ── J: SMCL require_bos=true (BOS regime filter) ────────
        // requireBos=true means only trade in bullish structure
        smcl(s, "AS8", "REQBOS", smcl7, "require_bos", true);
        smcl(s, "AS8", "REQBOS_BOSX", smcl7, "require_bos", true, "bos_exit", true);
        for (int sw : new int[]{3, 5, 10}) {
            smcl(s, "AS8", "REQBOS_SW" + sw, smcl7, "require_bos", true,
                    "swing_left", sw, "swing_right", 2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R9: Try ALL strategy types + creative combos
    //  Current ceiling: 50.7 (SMCL SW20). Try to break through.
    //  Survey: QuantStrategy types + SMCL exit_ema + BB filter
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR9(Map<String, Supplier<Strategy>> s) {

        // ── Control: Best SMCL (50.7) ─────────────────────────────
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "ha_flip");
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.5);
        best.put("trail_activation_pct", 3.0);
        best.put("st_flip_exit", true);
        best.put("st_period", 7);
        best.put("st_factor", 3.0);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        smcl(s, "AS9", "BEST", best);

        // ── A: SMCL + exit_ema (exit on price below short EMA) ──
        // Instead of ha_flip_exit, use exit_ema. Less aggressive exit.
        Map<String, Object> exEma = new HashMap<>(best);
        exEma.put("ha_flip_exit", false);
        for (int ee : new int[]{5, 8, 10, 13, 15, 20}) {
            smcl(s, "AS9", "XEMA" + ee, exEma, "exit_ema", ee);
        }
        // Exit EMA + HA flip exit (both active)
        for (int ee : new int[]{5, 10, 20}) {
            smcl(s, "AS9", "HAFX_XEMA" + ee, best, "exit_ema", ee);
        }

        // ── B: HA + BB width filter (only trade volatile stocks) ─
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);

        for (double bbw : new double[]{2.0, 3.0, 4.0, 5.0, 6.0, 8.0}) {
            ha(s, "AS9_HA", String.format("BBW%.0f", bbw), haBase, "bb_filter", true, "bb_min_width", bbw);
        }

        // ── C: HA + strong candle (no lower wick) ───────────────
        ha(s, "AS9_HA", "STRONG", haBase, "strong_candle", true);
        ha(s, "AS9_HA", "STRONG_CB2", haBase, "strong_candle", true, "confirm_bars", 2);

        // ── D: QuantStrategy — Donchian Breakout ────────────────
        Map<String, Object> qBase = new HashMap<>();
        qBase.put("atr_period", 20);
        qBase.put("sl_atr_mult", 0.0);
        qBase.put("tp_atr_mult", 0.0);
        qBase.put("trend_ema", 0);

        qBase.put("strategy_type", "donchian");
        for (int entry : new int[]{10, 20, 30, 50, 100}) {
            for (int exit : new int[]{5, 10, 20}) {
                if (exit >= entry) continue;
                quant(s, "AS9_Q", String.format("DC%d_%d", entry, exit), qBase,
                        "donchian_entry", entry, "donchian_exit", exit);
            }
        }
        // Donchian with SL
        quant(s, "AS9_Q", "DC20_10_SL2", qBase, "donchian_entry", 20, "donchian_exit", 10, "sl_atr_mult", 2.0);
        quant(s, "AS9_Q", "DC50_20_SL3", qBase, "donchian_entry", 50, "donchian_exit", 20, "sl_atr_mult", 3.0);

        // ── E: QuantStrategy — Chandelier Exit ──────────────────
        qBase.put("strategy_type", "chandelier");
        for (int p : new int[]{14, 22, 30}) {
            for (double m : new double[]{2.0, 3.0, 4.0}) {
                quant(s, "AS9_Q", String.format("CH%d_M%.0f", p, m), qBase,
                        "chandelier_period", p, "chandelier_mult", m);
            }
        }

        // ── F: QuantStrategy — Dual Momentum ────────────────────
        qBase.put("strategy_type", "dual_mom");
        for (int lb : new int[]{60, 126, 200, 252}) {
            for (int ema : new int[]{5, 10, 20}) {
                quant(s, "AS9_Q", String.format("MOM%d_E%d", lb, ema), qBase,
                        "mom_lookback", lb, "mom_ema", ema);
            }
        }

        // ── G: QuantStrategy — Mean Reversion in Trend ──────────
        qBase.put("strategy_type", "mean_revert");
        for (int rp : new int[]{2, 3, 5}) {
            for (double re : new double[]{10.0, 20.0, 30.0}) {
                quant(s, "AS9_Q", String.format("MR_RSI%d_E%.0f", rp, re), qBase,
                        "mr_rsi_period", rp, "mr_rsi_entry", re, "mr_trend_ema", 200);
            }
        }
        // MR without trend filter
        for (double re : new double[]{10.0, 20.0}) {
            quant(s, "AS9_Q", String.format("MR_RSI2_E%.0f_NT", re), qBase,
                    "mr_rsi_period", 2, "mr_rsi_entry", re, "mr_trend_ema", 0);
        }

        // ── H: QuantStrategy — Volatility Breakout ──────────────
        qBase.put("strategy_type", "vol_breakout");
        for (double m : new double[]{0.4, 0.6, 0.8, 1.0}) {
            for (int lb : new int[]{10, 20, 30}) {
                quant(s, "AS9_Q", String.format("VB%.1f_L%d", m, lb), qBase,
                        "vb_mult", m, "vb_lookback", lb);
            }
        }

        // ── I: QuantStrategy — Keltner Squeeze ──────────────────
        qBase.put("strategy_type", "squeeze");
        for (int len : new int[]{15, 20, 30}) {
            for (double km : new double[]{1.0, 1.5, 2.0}) {
                quant(s, "AS9_Q", String.format("SQ%d_K%.1f", len, km), qBase,
                        "kc_len", len, "kc_mult", km, "bb_len", len);
            }
        }

        // ── J: QuantStrategy — Heikin Ashi (from Quant wrapper) ─
        qBase.put("strategy_type", "heikin_ashi");
        quant(s, "AS9_Q", "HA_SL0", qBase, "sl_atr_mult", 0.0);
        quant(s, "AS9_Q", "HA_SL2", qBase, "sl_atr_mult", 2.0, "tp_atr_mult", 4.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R10: Fresh data (2242 stocks, 2010-Apr 2026)
    //  Re-baseline + broad sweep to find new optimum
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR10(Map<String, Supplier<Strategy>> s) {

        // ── SMCL best from R8 (was 50.7 on old data) ────────────
        Map<String, Object> smclBest = new HashMap<>();
        smclBest.put("require_bos", false);
        smclBest.put("trend_ema", 0);
        smclBest.put("entry_mode", "ha_flip");
        smclBest.put("bos_entry", false);
        smclBest.put("ha_flip_exit", true);
        smclBest.put("sl_atr_mult", 0.0);
        smclBest.put("trailing_stop", true);
        smclBest.put("trail_atr_mult", 0.5);
        smclBest.put("trail_activation_pct", 3.0);
        smclBest.put("st_flip_exit", true);
        smclBest.put("st_period", 7);
        smclBest.put("st_factor", 3.0);
        smclBest.put("atr_period", 20);
        smclBest.put("swing_left", 20);
        smclBest.put("swing_right", 2);
        smcl(s, "R10", "SMCL_SW20", smclBest);

        // ── HA baseline (was 50.3) ──────────────────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);
        ha(s, "R10", "HA_BASE", haBase);

        // ── A: SMCL swing sweep (SW20 was marginal winner) ──────
        for (int sw : new int[]{10, 15, 20, 25, 30, 40, 50}) {
            smcl(s, "R10", "SW" + sw, smclBest, "swing_left", sw);
        }

        // ── B: SMCL trail/activation grid ───────────────────────
        for (double tr : new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 1.0}) {
            for (double a : new double[]{1.0, 2.0, 3.0, 4.0, 5.0}) {
                smcl(s, "R10", String.format("TR%.1f_A%.0f", tr, a), smclBest,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── C: HA trail/activation grid ─────────────────────────
        for (double tr : new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 1.0}) {
            for (double a : new double[]{1.0, 2.0, 3.0, 4.0, 5.0}) {
                ha(s, "R10_HA", String.format("TR%.1f_A%.0f", tr, a), haBase,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── D: SMCL ST period/factor grid ───────────────────────
        for (int p : new int[]{5, 7, 10, 14, 20}) {
            for (double f : new double[]{2.0, 3.0, 4.0, 5.0}) {
                smcl(s, "R10", String.format("ST%d_F%.0f", p, f), smclBest,
                        "st_period", p, "st_factor", f);
            }
        }

        // ── E: SMCL no ST exit (pure HA flip + trail) ───────────
        smcl(s, "R10", "NOSTE", smclBest, "st_flip_exit", false);
        // No ST + different trail
        for (double tr : new double[]{0.3, 0.5, 0.7, 1.0}) {
            smcl(s, "R10", String.format("NOSTE_TR%.1f", tr), smclBest,
                    "st_flip_exit", false, "trail_atr_mult", tr);
        }

        // ── F: SMCL + bos_exit ──────────────────────────────────
        smcl(s, "R10", "BOSX", smclBest, "bos_exit", true);
        for (int sw : new int[]{10, 20, 30}) {
            smcl(s, "R10", "BOSX_SW" + sw, smclBest, "bos_exit", true, "swing_left", sw);
        }

        // ── G: ATR period sweep ─────────────────────────────────
        for (int atr : new int[]{7, 10, 14, 20, 30}) {
            smcl(s, "R10", "ATR" + atr, smclBest, "atr_period", atr);
            ha(s, "R10_HA", "ATR" + atr, haBase, "atr_period", atr);
        }

        // ── H: Cooldown sweep ───────────────────────────────────
        for (int cd : new int[]{2, 3, 5}) {
            smcl(s, "R10", "CD" + cd, smclBest, "cooldown_bars", cd);
        }

        // ── I: HA exit_bearish_bars (hold through pullbacks) ────
        Map<String, Object> haEb = new HashMap<>(haBase);
        haEb.put("ha_flip_exit", false);
        haEb.put("trail_atr_mult", 3.0);
        haEb.put("trail_activation_pct", 2.0);
        for (int eb : new int[]{2, 3, 4}) {
            ha(s, "R10_HA", "EB" + eb, haEb, "exit_bearish_bars", eb);
        }

        // ── J: HA confirm_bars ──────────────────────────────────
        ha(s, "R10_HA", "CB2", haBase, "confirm_bars", 2);
        ha(s, "R10_HA", "CB3", haBase, "confirm_bars", 3);

        // ── K: HA regime entry (RSI dip-buying) ─────────────────
        Map<String, Object> regime = new HashMap<>(haBase);
        regime.put("entry_mode", "regime");
        regime.put("rsi_period", 2);
        regime.put("rsi_entry", 30.0);
        for (int rw : new int[]{7, 9, 12}) {
            for (double rt : new double[]{0.3, 0.4, 0.5}) {
                ha(s, "R10_HA", String.format("REG_W%d_T%.1f", rw, rt), regime,
                        "regime_window", rw, "regime_threshold", rt);
            }
        }

        // ── L: HA pullback entry ────────────────────────────────
        Map<String, Object> pb = new HashMap<>(haBase);
        pb.put("entry_mode", "pullback");
        for (int mt : new int[]{3, 5, 7}) {
            for (int mp : new int[]{1, 2, 3}) {
                ha(s, "R10_HA", String.format("PB_MT%d_MP%d", mt, mp), pb,
                        "min_trend_bars", mt, "max_pullback_bars", mp);
            }
        }

        // ── M: SMCL ha_exit_bars (delayed HA exit) ──────────────
        for (int heb : new int[]{2, 3}) {
            smcl(s, "R10", "HEB" + heb, smclBest, "ha_exit_bars", heb);
        }

        // ── N: SMCL + exit EMA ──────────────────────────────────
        Map<String, Object> smclNoHa = new HashMap<>(smclBest);
        smclNoHa.put("ha_flip_exit", false);
        for (int ee : new int[]{5, 8, 10, 15, 20}) {
            smcl(s, "R10", "XEMA" + ee, smclNoHa, "exit_ema", ee);
        }

        // ── O: Volume filter ────────────────────────────────────
        ha(s, "R10_HA", "VOL1.0", haBase, "volume_filter", true, "vol_mult", 1.0, "vol_period", 20);
        ha(s, "R10_HA", "VOL1.5", haBase, "volume_filter", true, "vol_mult", 1.5, "vol_period", 20);

        // ── P: ADX filter ───────────────────────────────────────
        for (double adx : new double[]{15.0, 20.0, 25.0}) {
            ha(s, "R10_HA", String.format("ADX%.0f", adx), haBase,
                    "adx_filter", true, "adx_threshold", adx);
        }

        // ── Q: EMA trend filter ─────────────────────────────────
        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "R10_HA", "EMA" + ema, haBase, "trend_ema", ema);
        }

        // ── R: BB width filter ──────────────────────────────────
        for (double bbw : new double[]{3.0, 5.0, 8.0}) {
            ha(s, "R10_HA", String.format("BBW%.0f", bbw), haBase,
                    "bb_filter", true, "bb_min_width", bbw);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  ALL-STOCK R11: Push beyond 52.4 on 2242 stocks (fresh data)
    //  Focus: fine-tune trail, SL to cap losers, bos_entry, new combos
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR11(Map<String, Supplier<Strategy>> s) {

        // ── SMCL base (52.4 on fresh data) ──────────────────────
        // Note: ST flip exit has NO effect, keeping it off for simplicity
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.5);
        base.put("trail_activation_pct", 3.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R11", "BASE", base);

        // ── A: Fine trail grid (0.40-0.60 × 2.0-4.0) ───────────
        double[] trs = {0.40, 0.45, 0.50, 0.55, 0.60};
        double[] acts = {2.0, 2.5, 3.0, 3.5, 4.0};
        for (double tr : trs) {
            for (double a : acts) {
                smcl(s, "R11", String.format("TR%.2f_A%.1f", tr, a), base,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── B: SL to cap worst losses (boost pctProfitable) ────
        // HA flip exit fires fast, but stocks that gap down overnight
        // can still produce large losses. SL might catch those.
        for (double sl : new double[]{2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0}) {
            smcl(s, "R11", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── C: SL + trail fine-tune ─────────────────────────────
        for (double sl : new double[]{3.0, 5.0, 8.0}) {
            for (double tr : new double[]{0.45, 0.50, 0.55}) {
                smcl(s, "R11", String.format("SL%.0f_TR%.2f", sl, tr), base,
                        "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── D: bos_entry=true (enter on BOS bars too) ───────────
        // More entry signals = more trades = potentially more profitable stocks
        smcl(s, "R11", "BOSE", base, "bos_entry", true);
        smcl(s, "R11", "BOSE_BOSX", base, "bos_entry", true, "bos_exit", true);
        for (int sw : new int[]{5, 10, 20, 30}) {
            smcl(s, "R11", "BOSE_SW" + sw, base, "bos_entry", true, "swing_left", sw);
        }

        // ── E: require_bos=true (only trade in bullish structure) ─
        // Filters: only enter ha_flip when BOS is bullish
        // May improve pctProfitable by avoiding entries in bearish regime
        smcl(s, "R11", "REQBOS", base, "require_bos", true);
        for (int sw : new int[]{5, 10, 20, 30}) {
            smcl(s, "R11", "REQBOS_SW" + sw, base, "require_bos", true, "swing_left", sw);
        }

        // ── F: SMCL pullback entry (within bullish HA context) ──
        smcl(s, "R11", "PB_E10", base, "entry_mode", "pullback", "entry_ema", 10);
        smcl(s, "R11", "PB_E5", base, "entry_mode", "pullback", "entry_ema", 5);
        smcl(s, "R11", "PB_E20", base, "entry_mode", "pullback", "entry_ema", 20);

        // ── G: SMCL RSI entry ───────────────────────────────────
        for (double rsi : new double[]{30.0, 40.0, 50.0, 60.0}) {
            smcl(s, "R11", String.format("RSI%.0f", rsi), base,
                    "entry_mode", "rsi", "rsi_period", 2, "rsi_threshold", rsi);
        }

        // ── H: HA with fine trail grid ──────────────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);

        for (double tr : trs) {
            for (double a : acts) {
                ha(s, "R11_HA", String.format("TR%.2f_A%.1f", tr, a), haBase,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── I: HA + SL ─────────────────────────────────────────
        for (double sl : new double[]{3.0, 5.0, 8.0}) {
            ha(s, "R11_HA", String.format("SL%.0f", sl), haBase, "sl_atr_mult", sl);
        }

        // ── J: HA + strong candle + confirm_bars combos ─────────
        ha(s, "R11_HA", "STRONG", haBase, "strong_candle", true);
        ha(s, "R11_HA", "CB2", haBase, "confirm_bars", 2);
        // Strong + different trail
        for (double tr : new double[]{0.4, 0.5, 0.6}) {
            ha(s, "R11_HA", String.format("STRONG_TR%.1f", tr), haBase,
                    "strong_candle", true, "trail_atr_mult", tr);
        }

        // ── K: Cooldown (reduce overtrading on noisy stocks) ────
        for (int cd : new int[]{1, 2, 3}) {
            smcl(s, "R11", "CD" + cd, base, "cooldown_bars", cd);
            ha(s, "R11_HA", "CD" + cd, haBase, "cooldown_bars", cd);
        }

        // ── L: Max consecutive losses ───────────────────────────
        for (int mcl : new int[]{2, 3, 4}) {
            smcl(s, "R11", "MCL" + mcl, base, "max_consec_losses", mcl, "cooldown_bars", 5);
        }

        // ── M: EMA slope filter on HA ───────────────────────────
        // Only enter when trend EMA is rising — avoids downtrending stocks
        for (int ema : new int[]{50, 100}) {
            for (int slope : new int[]{3, 5, 10}) {
                ha(s, "R11_HA", String.format("EMA%d_SLP%d", ema, slope), haBase,
                        "trend_ema", ema, "ema_slope_bars", slope);
            }
        }

        // ══════════════════════════════════════════════════════════
        //  N: WEEKLY timeframe — smoother HA, fewer false signals
        //  Weekly bars aggregate Mon-Fri into single OHLCV bar
        // ══════════════════════════════════════════════════════════

        // HA weekly base
        Map<String, Object> haW = new HashMap<>();
        haW.put("entry_mode", "flip");
        haW.put("ha_flip_exit", true);
        haW.put("trailing_stop", true);
        haW.put("sl_atr_mult", 0.0);
        haW.put("tp_atr_mult", 0.0);
        haW.put("timeframe", "1W");

        // Trail sweep for weekly (weekly bars have bigger moves, different optimal trail)
        for (double tr : new double[]{0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0}) {
            for (double a : new double[]{2.0, 3.0, 5.0, 8.0}) {
                ha(s, "R11_W", String.format("TR%.1f_A%.0f", tr, a), haW,
                        "trail_atr_mult", tr, "trail_activation_pct", a, "atr_period", 14);
            }
        }

        // Weekly with no trailing (just HA flip exit)
        ha(s, "R11_W", "NOTR", haW, "trailing_stop", false, "atr_period", 14);

        // Weekly + exit_bearish_bars
        Map<String, Object> haWb = new HashMap<>(haW);
        haWb.put("ha_flip_exit", false);
        haWb.put("atr_period", 14);
        for (int eb : new int[]{1, 2, 3}) {
            for (double tr : new double[]{1.0, 2.0, 3.0}) {
                ha(s, "R11_W", String.format("EB%d_TR%.0f", eb, tr), haWb,
                        "exit_bearish_bars", eb, "trailing_stop", true,
                        "trail_atr_mult", tr, "trail_activation_pct", 3.0);
            }
        }

        // Weekly ATR period sweep
        for (int atr : new int[]{7, 10, 14, 20}) {
            ha(s, "R11_W", "ATR" + atr, haW, "trail_atr_mult", 1.0, "trail_activation_pct", 3.0, "atr_period", atr);
        }

        // SMCL weekly
        Map<String, Object> smclW = new HashMap<>(base);
        smclW.put("timeframe", "1W");
        smclW.put("st_flip_exit", false); // ST is irrelevant
        for (double tr : new double[]{0.5, 1.0, 2.0, 3.0}) {
            for (double a : new double[]{3.0, 5.0, 8.0}) {
                smcl(s, "R11_W", String.format("SMCL_TR%.1f_A%.0f", tr, a), smclW,
                        "trail_atr_mult", tr, "trail_activation_pct", a, "atr_period", 14);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R12: Improve Robustness — debug consistency, try new formulas
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR12(Map<String, Supplier<Strategy>> s) {
        // Best config from R11 — now with improved scoring (COV_CAP + 20% trim)
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 5.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R12", "BEST", base);

        // ── A: Trail sweep around optimal (tighter = less variance) ──
        double[] trs = {0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60};
        double[] acts = {2.0, 2.5, 3.0, 3.5, 4.0, 5.0};
        for (double tr : trs) {
            for (double a : acts) {
                if (tr == 0.50 && a == 3.5) continue; // skip BEST duplicate
                smcl(s, "R12", String.format("TR%.2f_A%.1f", tr, a), base,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── B: SL to cap worst losses (boost pctProfitable) ────
        for (double sl : new double[]{3.0, 5.0, 8.0, 10.0}) {
            smcl(s, "R12", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── C: SL + trail combos ───────────────────────────────
        for (double sl : new double[]{5.0, 8.0}) {
            for (double tr : new double[]{0.35, 0.40, 0.50}) {
                for (double a : new double[]{3.0, 3.5, 4.0}) {
                    smcl(s, "R12", String.format("SL%.0f_TR%.2f_A%.1f", sl, tr, a), base,
                            "sl_atr_mult", sl, "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ── D: require_bos (may reduce bad trades on losing stocks) ──
        smcl(s, "R12", "REQBOS", base, "require_bos", true);
        for (int sw : new int[]{5, 10, 20}) {
            smcl(s, "R12", "REQBOS_SW" + sw, base, "require_bos", true, "swing_left", sw);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R13: Push past 90 — strategy changes to boost pctProfitable + scoring
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR13(Map<String, Supplier<Strategy>> s) {
        // ── Best from R12 (score 90.6) ──
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 5.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R13", "BEST", base);

        // ── A: HA exit bars (require 2-3 consecutive bearish HA to exit) ──
        // Reduces whipsaw exits → may convert small losses to gains
        for (int eb : new int[]{2, 3, 4, 5}) {
            smcl(s, "R13", "HEB" + eb, base, "ha_exit_bars", eb);
        }

        // ── B: Cooldown bars (skip re-entry after exit) ──
        // Avoids re-entering on choppy stocks that whipsaw
        for (int cd : new int[]{2, 3, 5, 7, 10}) {
            smcl(s, "R13", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── C: ADX filter (only trade trending stocks) ──
        for (double adx : new double[]{15, 20, 25}) {
            smcl(s, "R13", String.format("ADX%.0f", adx), base,
                    "adx_filter", true, "adx_threshold", adx);
        }

        // ── D: HA strategy with exit_bearish_bars (anti-whipsaw) ──
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 5.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 20);

        ha(s, "R13_HA", "BASE", haBase);
        for (int eb : new int[]{2, 3, 4, 5}) {
            ha(s, "R13_HA", "EB" + eb, haBase, "exit_bearish_bars", eb);
        }

        // ── E: HA confirm_bars (require 2+ bullish HA to enter) ──
        for (int cb : new int[]{2, 3}) {
            ha(s, "R13_HA", "CB" + cb, haBase, "confirm_bars", cb);
        }

        // ── F: HA cooldown ──
        for (int cd : new int[]{3, 5, 10}) {
            ha(s, "R13_HA", "CD" + cd, haBase, "cooldown_bars", cd);
        }

        // ── G: HA with ADX filter ──
        for (double adx : new double[]{15, 20, 25}) {
            ha(s, "R13_HA", String.format("ADX%.0f", adx), haBase,
                    "adx_filter", true, "adx_threshold", adx);
        }

        // ── H: HA volume filter ──
        for (double vm : new double[]{1.0, 1.5, 2.0}) {
            ha(s, "R13_HA", String.format("VOL%.1f", vm), haBase,
                    "volume_filter", true, "vol_mult", vm);
        }

        // ── I: Combos — exit_bearish + cooldown ──
        for (int eb : new int[]{2, 3}) {
            for (int cd : new int[]{3, 5}) {
                ha(s, "R13_HA", String.format("EB%d_CD%d", eb, cd), haBase,
                        "exit_bearish_bars", eb, "cooldown_bars", cd);
            }
        }

        // ── J: SMCL HA exit bars + trail combos ──
        for (int eb : new int[]{2, 3}) {
            for (double tr : new double[]{0.40, 0.50, 0.60}) {
                smcl(s, "R13", String.format("HEB%d_TR%.2f", eb, tr), base,
                        "ha_exit_bars", eb, "trail_atr_mult", tr);
            }
        }

        // ── K: SMCL cooldown + trail combos ──
        for (int cd : new int[]{3, 5}) {
            for (double tr : new double[]{0.40, 0.50, 0.60}) {
                smcl(s, "R13", String.format("CD%d_TR%.2f", cd, tr), base,
                        "cooldown_bars", cd, "trail_atr_mult", tr);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R14: Target 99+ — maximize pctProfitable & consistency
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR14(Map<String, Supplier<Strategy>> s) {

        // ── Current best baseline ──
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 8.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R14", "BEST", base);

        // ── A: No trailing stop — pure HA flip entry/exit ──
        // Fewer exits = fewer losing round-trips on choppy stocks
        smcl(s, "R14", "NOTR", base, "trailing_stop", false);

        // ── B: Very high trail activation (trail rarely fires) ──
        for (double a : new double[]{8.0, 10.0, 15.0, 20.0, 30.0, 50.0}) {
            smcl(s, "R14", String.format("A%.0f", a), base, "trail_activation_pct", a);
        }

        // ── C: Very wide trailing (like a loose safety net) ──
        for (double tr : new double[]{1.0, 1.5, 2.0, 3.0, 5.0}) {
            smcl(s, "R14", String.format("WTR%.1f", tr), base,
                    "trail_atr_mult", tr, "trail_activation_pct", 10.0);
        }

        // ── D: HA with no trailing (purest HA signal) ──
        Map<String, Object> haPure = new HashMap<>();
        haPure.put("entry_mode", "flip");
        haPure.put("ha_flip_exit", true);
        haPure.put("trailing_stop", false);
        haPure.put("sl_atr_mult", 0.0);
        haPure.put("tp_atr_mult", 0.0);
        haPure.put("atr_period", 20);
        ha(s, "R14_HA", "PURE", haPure);

        // ── E: HA with very loose trailing ──
        Map<String, Object> haLoose = new HashMap<>(haPure);
        haLoose.put("trailing_stop", true);
        for (double tr : new double[]{1.0, 2.0, 3.0, 5.0}) {
            for (double a : new double[]{10.0, 20.0, 50.0}) {
                ha(s, "R14_HA", String.format("TR%.0f_A%.0f", tr, a), haLoose,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── F: SMCL ha_exit_bars=2 + higher activation ──
        // HEB2 had same robustness as BEST — combine with wider trail
        for (double a : new double[]{5.0, 8.0, 10.0, 20.0}) {
            smcl(s, "R14", String.format("HEB2_A%.0f", a), base,
                    "ha_exit_bars", 2, "trail_activation_pct", a);
        }

        // ── G: SMCL ha_exit_bars=2 + no trailing ──
        smcl(s, "R14", "HEB2_NOTR", base, "ha_exit_bars", 2, "trailing_stop", false);

        // ── H: Different ATR periods (smoother trailing) ──
        for (int atr : new int[]{30, 40, 50}) {
            smcl(s, "R14", "ATR" + atr, base, "atr_period", atr);
        }

        // ── I: Swing lookback variations ──
        for (int sw : new int[]{5, 10, 30, 40, 50}) {
            smcl(s, "R14", "SW" + sw, base, "swing_left", sw);
        }

        // ── J: SMCL with confirm (ha_exit_bars=0 but delayed entry) ──
        // Fewer entries = fewer losing trades on choppy stocks
        // Note: SMCL uses "ha_flip" entry which checks HA bullish.
        // Different swing_right values affect BOS detection lag
        for (int sr : new int[]{1, 3, 5}) {
            smcl(s, "R14", "SR" + sr, base, "swing_right", sr);
        }

        // ── K: Extreme: HA flip with exit_bearish_bars=3 ──
        // Holds through short dips — may improve pctProfitable
        Map<String, Object> haEB = new HashMap<>(haPure);
        haEB.put("ha_flip_exit", false); // disable instant flip exit
        for (int eb : new int[]{2, 3, 4, 5, 7, 10}) {
            ha(s, "R14_HA", "EB" + eb, haEB,
                    "exit_bearish_bars", eb);
        }

        // ── L: HA exit_bearish_bars + loose trailing safety ──
        for (int eb : new int[]{3, 5}) {
            for (double tr : new double[]{2.0, 3.0, 5.0}) {
                ha(s, "R14_HA", String.format("EB%d_TR%.0f", eb, tr), haEB,
                        "exit_bearish_bars", eb, "trailing_stop", true,
                        "trail_atr_mult", tr, "trail_activation_pct", 10.0);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R15: Reduce MaxDD & increase Win Rate
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR15(Map<String, Supplier<Strategy>> s) {

        // ── Baseline (R14 best: score 99.4, MDD -49.6%, WR 39.0%) ──
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 8.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R15", "BASE", base);

        // ═══════════════════════════════════════════════════════════
        //  GROUP 1: REDUCE MAX DRAWDOWN
        // ═══════════════════════════════════════════════════════════

        // ── A: Stop Loss (caps worst single-trade loss) ──
        // SL fires intra-bar at engine level — catches overnight gaps
        for (double sl : new double[]{3.0, 4.0, 5.0, 6.0, 8.0, 10.0}) {
            smcl(s, "R15", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── B: Trend EMA filter (avoid bear market entries) ──
        // Only enter HA flip when close > EMA → skips entries in downtrends
        for (int ema : new int[]{50, 100, 200}) {
            smcl(s, "R15", "EMA" + ema, base, "trend_ema", ema);
        }

        // ── C: Lower trail activation (protect profits sooner) ──
        for (double a : new double[]{1.0, 2.0, 3.0, 4.0, 5.0}) {
            smcl(s, "R15", String.format("ACT%.0f", a), base, "trail_activation_pct", a);
        }

        // ── D: SL + trend EMA combos ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (int ema : new int[]{50, 100, 200}) {
                smcl(s, "R15", String.format("SL%.0f_EMA%d", sl, ema), base,
                        "sl_atr_mult", sl, "trend_ema", ema);
            }
        }

        // ── E: SL + lower activation ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (double a : new double[]{3.0, 5.0}) {
                smcl(s, "R15", String.format("SL%.0f_ACT%.0f", sl, a), base,
                        "sl_atr_mult", sl, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════════
        //  GROUP 2: INCREASE WIN RATE
        // ═══════════════════════════════════════════════════════════

        // ── F: ha_exit_bars (hold through noise → fewer small losses) ──
        for (int heb : new int[]{2, 3, 4, 5}) {
            smcl(s, "R15", "HEB" + heb, base, "ha_exit_bars", heb);
        }

        // ── G: Tighter trail + lower activation (lock in small gains) ──
        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40}) {
            for (double a : new double[]{1.0, 2.0, 3.0}) {
                smcl(s, "R15", String.format("TT%.2f_A%.0f", tr, a), base,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════════
        //  GROUP 3: COMBOS (reduce MDD AND increase WR)
        // ═══════════════════════════════════════════════════════════

        // ── H: SL + ha_exit_bars ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (int heb : new int[]{2, 3}) {
                smcl(s, "R15", String.format("SL%.0f_HEB%d", sl, heb), base,
                        "sl_atr_mult", sl, "ha_exit_bars", heb);
            }
        }

        // ── I: EMA + ha_exit_bars ──
        for (int ema : new int[]{50, 100, 200}) {
            for (int heb : new int[]{2, 3}) {
                smcl(s, "R15", String.format("EMA%d_HEB%d", ema, heb), base,
                        "trend_ema", ema, "ha_exit_bars", heb);
            }
        }

        // ── J: SL + EMA + lower activation ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (int ema : new int[]{50, 100}) {
                for (double a : new double[]{3.0, 5.0}) {
                    smcl(s, "R15", String.format("SL%.0f_EMA%d_A%.0f", sl, ema, a), base,
                            "sl_atr_mult", sl, "trend_ema", ema, "trail_activation_pct", a);
                }
            }
        }

        // ── K: SL + tighter trail + lower activation ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (double tr : new double[]{0.30, 0.40}) {
                for (double a : new double[]{2.0, 3.0}) {
                    smcl(s, "R15", String.format("SL%.0f_TT%.2f_A%.0f", sl, tr, a), base,
                            "sl_atr_mult", sl, "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ── L: Full combo: SL + EMA + ha_exit_bars ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (int ema : new int[]{50, 100}) {
                for (int heb : new int[]{2, 3}) {
                    smcl(s, "R15", String.format("SL%.0f_EMA%d_HEB%d", sl, ema, heb), base,
                            "sl_atr_mult", sl, "trend_ema", ema, "ha_exit_bars", heb);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R15b: Fine-tune around ACT1 sweet spot (MDD↓ WR↑)
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR15b(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 1.0);  // ACT1 as new baseline
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);

        // ── A: Trail mult fine-tune around 0.50 with ACT1 ──
        for (double tr : new double[]{0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.70, 0.80}) {
            smcl(s, "R15b", String.format("TR%.2f", tr), base, "trail_atr_mult", tr);
        }

        // ── B: SL with ACT1 ──
        for (double sl : new double[]{3.0, 4.0, 5.0, 6.0, 8.0, 10.0}) {
            smcl(s, "R15b", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── C: SL + trail combos with ACT1 ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (double tr : new double[]{0.35, 0.40, 0.50, 0.60}) {
                smcl(s, "R15b", String.format("SL%.0f_TR%.2f", sl, tr), base,
                        "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }

        // ── D: ha_exit_bars with ACT1 ──
        for (int heb : new int[]{2, 3}) {
            smcl(s, "R15b", "HEB" + heb, base, "ha_exit_bars", heb);
        }

        // ── E: ha_exit_bars + SL with ACT1 ──
        for (int heb : new int[]{2, 3}) {
            for (double sl : new double[]{5.0, 8.0}) {
                smcl(s, "R15b", String.format("HEB%d_SL%.0f", heb, sl), base,
                        "ha_exit_bars", heb, "sl_atr_mult", sl);
            }
        }

        // ── F: Fine activation sweep (0.5%, 1%, 1.5%, 2%) ──
        for (double a : new double[]{0.5, 1.5}) {
            smcl(s, "R15b", String.format("ACT%.1f", a), base, "trail_activation_pct", a);
        }

        // ── G: ATR period with ACT1 ──
        for (int atr : new int[]{10, 14, 30}) {
            smcl(s, "R15b", "ATR" + atr, base, "atr_period", atr);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R15c: Combine HEB (high WR) + tight trail (low MDD)
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR15c(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 1.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);

        // Baselines
        smcl(s, "R15c", "ACT1", base);  // WR 43.5%, MDD -45.7%
        smcl(s, "R15c", "ACT8", base, "trail_activation_pct", 8.0);  // old best

        // ── A: HEB2 + different trail tightness ──
        // HEB2 gave WR 45.7% but MDD -48.7%. Tighter trail should cap MDD.
        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40, 0.50, 0.60}) {
            smcl(s, "R15c", String.format("HEB2_TR%.2f", tr), base,
                    "ha_exit_bars", 2, "trail_atr_mult", tr);
        }

        // ── B: HEB3 + tighter trail ──
        // HEB3 gave WR 48.8% but MDD -50.5%. Trail should cap MDD.
        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40, 0.50}) {
            smcl(s, "R15c", String.format("HEB3_TR%.2f", tr), base,
                    "ha_exit_bars", 3, "trail_atr_mult", tr);
        }

        // ── C: HEB + SL + trail ──
        for (int heb : new int[]{2, 3}) {
            for (double sl : new double[]{5.0, 8.0}) {
                for (double tr : new double[]{0.30, 0.40, 0.50}) {
                    smcl(s, "R15c", String.format("HEB%d_SL%.0f_TR%.2f", heb, sl, tr), base,
                            "ha_exit_bars", heb, "sl_atr_mult", sl, "trail_atr_mult", tr);
                }
            }
        }

        // ── D: HEB + activation fine-tune ──
        for (int heb : new int[]{2, 3}) {
            for (double a : new double[]{0.5, 1.0, 2.0, 3.0}) {
                smcl(s, "R15c", String.format("HEB%d_A%.1f", heb, a), base,
                        "ha_exit_bars", heb, "trail_activation_pct", a);
            }
        }

        // ── E: Best combos with SL for MDD protection ──
        for (double sl : new double[]{4.0, 5.0, 6.0, 8.0}) {
            smcl(s, "R15c", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── F: SL + tighter trail combos ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (double tr : new double[]{0.35, 0.40}) {
                smcl(s, "R15c", String.format("SL%.0f_TR%.2f", sl, tr), base,
                        "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R16: Fine-tune ACT1 — push WR↑ MDD↓ CAGR↑
    // ══════════════════════════════════════════════════════════════
    private static void addAllStockR16(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 1.0);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R16", "BASE", base);  // ACT1 baseline

        // ── A: Fine activation sweep (0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0) ──
        for (double a : new double[]{0.0, 0.25, 0.5, 0.75, 1.25, 1.5, 2.0}) {
            smcl(s, "R16", String.format("A%.2f", a), base, "trail_activation_pct", a);
        }

        // ── B: Trail mult fine sweep with ACT1 ──
        for (double tr : new double[]{0.30, 0.35, 0.40, 0.45, 0.55, 0.60, 0.70}) {
            smcl(s, "R16", String.format("TR%.2f", tr), base, "trail_atr_mult", tr);
        }

        // ── C: ATR period sweep with ACT1 ──
        for (int atr : new int[]{7, 10, 14, 30}) {
            smcl(s, "R16", "ATR" + atr, base, "atr_period", atr);
        }

        // ── D: Exit EMA — additional exit when close < EMA ──
        // Fires BEFORE HA flip on some bars → reduces losses
        for (int ema : new int[]{5, 8, 10, 13, 15, 20}) {
            smcl(s, "R16", "XEMA" + ema, base, "exit_ema", ema);
        }

        // ── E: Exit EMA + SL ──
        for (int ema : new int[]{8, 10, 13}) {
            for (double sl : new double[]{5.0, 8.0}) {
                smcl(s, "R16", String.format("XEMA%d_SL%.0f", ema, sl), base,
                        "exit_ema", ema, "sl_atr_mult", sl);
            }
        }

        // ── F: Best trail + best activation combos ──
        for (double tr : new double[]{0.40, 0.45, 0.50}) {
            for (double a : new double[]{0.5, 0.75, 1.0}) {
                if (tr == 0.50 && a == 1.0) continue; // skip BASE
                smcl(s, "R16", String.format("TR%.2f_A%.2f", tr, a), base,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── G: SL (MDD protection) with ACT1 ──
        for (double sl : new double[]{5.0, 6.0, 8.0}) {
            smcl(s, "R16", String.format("SL%.0f", sl), base, "sl_atr_mult", sl);
        }

        // ── H: SL + exit EMA ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (int ema : new int[]{10, 13}) {
                smcl(s, "R16", String.format("SL%.0f_XEMA%d", sl, ema), base,
                        "sl_atr_mult", sl, "exit_ema", ema);
            }
        }

        // ── I: SL + tighter trail ──
        for (double sl : new double[]{5.0, 8.0}) {
            for (double tr : new double[]{0.35, 0.40, 0.45}) {
                smcl(s, "R16", String.format("SL%.0f_TR%.2f", sl, tr), base,
                        "sl_atr_mult", sl, "trail_atr_mult", tr);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R17: Combination sweep — ha_exit_bars, cooldown, ST filter,
    //       BOS exit, and multi-winner combos
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR17(Map<String, Supplier<Strategy>> s) {

        // Best base from R16: TR0.45, ACT0.50
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.45);
        base.put("trail_activation_pct", 0.50);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        smcl(s, "R17", "BASE", base);  // TR0.45_A0.50

        // Also test the ACT1 base for comparison
        smcl(s, "R17", "ACT1", base, "trail_atr_mult", 0.50, "trail_activation_pct", 1.0);

        // ── A: ha_exit_bars — require N consecutive bearish HA bars before exit ──
        // This dramatically increases WR by reducing whipsaw exits
        for (int heb : new int[]{1, 2, 3}) {
            smcl(s, "R17", "HEB" + heb, base, "ha_exit_bars", heb);
        }

        // ── B: Cooldown bars — min bars between exit and next entry ──
        for (int cd : new int[]{1, 2, 3, 5}) {
            smcl(s, "R17", "CD" + cd, base, "cooldown_bars", cd);
        }

        // ── C: Supertrend trend filter — only enter when ST is bullish ──
        smcl(s, "R17", "STF", base, "st_trend_filter", true);
        smcl(s, "R17", "STF_F2", base, "st_trend_filter", true, "st_factor", 2.0);
        smcl(s, "R17", "STF_F4", base, "st_trend_filter", true, "st_factor", 4.0);
        smcl(s, "R17", "STF_P20", base, "st_trend_filter", true, "st_period", 20);

        // ── D: BOS exit — exit on bearish structure break (no BOS entry needed) ──
        smcl(s, "R17", "BOSX", base, "bos_exit", true);
        smcl(s, "R17", "BOSX_SW10", base, "bos_exit", true, "swing_left", 10, "swing_right", 2);
        smcl(s, "R17", "BOSX_SW5", base, "bos_exit", true, "swing_left", 5, "swing_right", 2);

        // ── E: ST flip exit — exit when Supertrend flips bearish ──
        smcl(s, "R17", "STEX", base, "st_flip_exit", true);
        smcl(s, "R17", "STEX_F2", base, "st_flip_exit", true, "st_factor", 2.0);
        smcl(s, "R17", "STEX_P20", base, "st_flip_exit", true, "st_period", 20);

        // ── F: ha_exit_bars + cooldown combos ──
        for (int heb : new int[]{1, 2}) {
            for (int cd : new int[]{1, 2, 3}) {
                smcl(s, "R17", String.format("HEB%d_CD%d", heb, cd), base,
                        "ha_exit_bars", heb, "cooldown_bars", cd);
            }
        }

        // ── G: ha_exit_bars + trail variations ──
        for (int heb : new int[]{1, 2}) {
            for (double tr : new double[]{0.40, 0.50, 0.60}) {
                smcl(s, "R17", String.format("HEB%d_TR%.2f", heb, tr), base,
                        "ha_exit_bars", heb, "trail_atr_mult", tr);
            }
        }

        // ── H: ha_exit_bars + activation ──
        for (int heb : new int[]{1, 2}) {
            for (double a : new double[]{0.0, 1.0, 2.0}) {
                smcl(s, "R17", String.format("HEB%d_A%.0f", heb, a), base,
                        "ha_exit_bars", heb, "trail_activation_pct", a);
            }
        }

        // ── I: ST filter + ha_exit_bars ──
        for (int heb : new int[]{1, 2}) {
            smcl(s, "R17", String.format("STF_HEB%d", heb), base,
                    "st_trend_filter", true, "ha_exit_bars", heb);
        }

        // ── J: BOS exit + ha_exit_bars ──
        for (int heb : new int[]{1, 2}) {
            smcl(s, "R17", String.format("BOSX_HEB%d", heb), base,
                    "bos_exit", true, "ha_exit_bars", heb);
        }

        // ── K: ST exit + ha_exit_bars ──
        for (int heb : new int[]{1, 2}) {
            smcl(s, "R17", String.format("STEX_HEB%d", heb), base,
                    "st_flip_exit", true, "ha_exit_bars", heb);
        }

        // ── L: Trend EMA filter (lighter than 200 — less restrictive) ──
        for (int ema : new int[]{50, 100, 150}) {
            smcl(s, "R17", "TEMA" + ema, base, "trend_ema", ema);
        }

        // ── M: Trend EMA + ha_exit_bars ──
        for (int ema : new int[]{50, 100}) {
            for (int heb : new int[]{1, 2}) {
                smcl(s, "R17", String.format("TEMA%d_HEB%d", ema, heb), base,
                        "trend_ema", ema, "ha_exit_bars", heb);
            }
        }

        // ── N: Triple combo — best of each dimension ──
        smcl(s, "R17", "HEB1_CD2_STF", base,
                "ha_exit_bars", 1, "cooldown_bars", 2, "st_trend_filter", true);
        smcl(s, "R17", "HEB2_CD2_STF", base,
                "ha_exit_bars", 2, "cooldown_bars", 2, "st_trend_filter", true);
        smcl(s, "R17", "HEB1_CD1_BOSX", base,
                "ha_exit_bars", 1, "cooldown_bars", 1, "bos_exit", true);
        smcl(s, "R17", "HEB1_STEX_CD2", base,
                "ha_exit_bars", 1, "st_flip_exit", true, "cooldown_bars", 2);
        smcl(s, "R17", "TEMA50_HEB1_CD2", base,
                "trend_ema", 50, "ha_exit_bars", 1, "cooldown_bars", 2);
        smcl(s, "R17", "TEMA100_HEB1_BOSX", base,
                "trend_ema", 100, "ha_exit_bars", 1, "bos_exit", true);
    }

    // ══════════════════════════════════════════════════════════════
    //  R18: Alternate entry modes + best combos from R17
    //       COV_CAP tuned to 15.0 in Scorer
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR18(Map<String, Supplier<Strategy>> s) {

        // Best config from R17: BOSX (BOS exit + TR0.45 + ACT0.50)
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "ha_flip");
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.45);
        best.put("trail_activation_pct", 0.50);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        best.put("bos_exit", true);
        smcl(s, "R18", "BOSX", best);  // R17 best

        // ACT1 for comparison
        smcl(s, "R18", "ACT1", best, "trail_atr_mult", 0.50, "trail_activation_pct", 1.0);

        // ── A: Pullback entry mode — close dips below entry EMA then crosses back ──
        Map<String, Object> pbBase = new HashMap<>(best);
        pbBase.put("entry_mode", "pullback");
        pbBase.put("bos_entry", false);
        for (int ema : new int[]{5, 8, 10, 15, 20}) {
            smcl(s, "R18", "PB_EMA" + ema, pbBase, "entry_ema", ema);
        }

        // Pullback + BOS entry (enter on pullback recovery OR BOS bar)
        for (int ema : new int[]{10, 15}) {
            smcl(s, "R18", "PB_BOS_EMA" + ema, pbBase, "entry_ema", ema, "bos_entry", true);
        }

        // ── B: RSI pullback entry ──
        Map<String, Object> rsiBase = new HashMap<>(best);
        rsiBase.put("entry_mode", "rsi");
        for (double thresh : new double[]{40, 50, 55, 60, 70}) {
            smcl(s, "R18", String.format("RSI_T%.0f", thresh), rsiBase, "rsi_threshold", thresh);
        }

        // RSI with different periods
        for (int period : new int[]{2, 5, 7, 14}) {
            smcl(s, "R18", "RSI_P" + period, rsiBase, "rsi_period", period, "rsi_threshold", 55.0);
        }

        // ── C: Supertrend flip entry ──
        Map<String, Object> stBase = new HashMap<>(best);
        stBase.put("entry_mode", "st_flip");
        stBase.put("ha_flip_exit", true);
        smcl(s, "R18", "STENT", stBase);
        smcl(s, "R18", "STENT_F2", stBase, "st_factor", 2.0);
        smcl(s, "R18", "STENT_F4", stBase, "st_factor", 4.0);
        smcl(s, "R18", "STENT_P20", stBase, "st_period", 20);
        smcl(s, "R18", "STENT_P7", stBase, "st_period", 7);

        // ST entry + ST exit (pure supertrend system + HA exit + trail)
        smcl(s, "R18", "STENT_STEX", stBase, "st_flip_exit", true);
        smcl(s, "R18", "STENT_STEX_F2", stBase, "st_flip_exit", true, "st_factor", 2.0);

        // ── D: BOS-only entry (most conservative — enter only on structure break) ──
        Map<String, Object> bosBase = new HashMap<>(best);
        bosBase.put("entry_mode", "bos_only");
        bosBase.put("bos_entry", true);
        bosBase.put("require_bos", true);
        smcl(s, "R18", "BOS_ENTRY", bosBase);
        smcl(s, "R18", "BOS_ENTRY_SW10", bosBase, "swing_left", 10);
        smcl(s, "R18", "BOS_ENTRY_SW5", bosBase, "swing_left", 5);

        // ── E: Any bullish entry (most aggressive) ──
        Map<String, Object> anyBase = new HashMap<>(best);
        anyBase.put("entry_mode", "any_bullish");
        smcl(s, "R18", "ANY_BULL", anyBase);

        // ── F: BOSX fine-tune — trail/activation combos ──
        for (double tr : new double[]{0.35, 0.40, 0.50}) {
            smcl(s, "R18", String.format("BOSX_TR%.2f", tr), best, "trail_atr_mult", tr);
        }
        for (double a : new double[]{0.0, 0.25, 0.75, 1.0}) {
            smcl(s, "R18", String.format("BOSX_A%.2f", a), best, "trail_activation_pct", a);
        }

        // ── G: BOSX + HEB2 (best WR combo from R17) ──
        smcl(s, "R18", "BOSX_HEB2", best, "ha_exit_bars", 2);
        smcl(s, "R18", "BOSX_HEB2_TR0.50", best, "ha_exit_bars", 2, "trail_atr_mult", 0.50);
        smcl(s, "R18", "BOSX_HEB2_TR0.60", best, "ha_exit_bars", 2, "trail_atr_mult", 0.60);

        // ── H: HA flip entry with require_bos + bos_exit (regime filtered) ──
        Map<String, Object> regBase = new HashMap<>(best);
        regBase.put("require_bos", true);
        smcl(s, "R18", "REGIME", regBase);
        smcl(s, "R18", "REGIME_SW10", regBase, "swing_left", 10);
        smcl(s, "R18", "REGIME_SW5", regBase, "swing_left", 5);
    }

    // ══════════════════════════════════════════════════════════════
    //  R19: RSI entry deep dive + BOSX combos
    //       Robustness = 5% pctProf + 95% consistency, COV_CAP=15
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR19(Map<String, Supplier<Strategy>> s) {

        // Best HA flip config (reference)
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("require_bos", false);
        haBase.put("trend_ema", 0);
        haBase.put("entry_mode", "ha_flip");
        haBase.put("bos_entry", false);
        haBase.put("ha_flip_exit", true);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.50);
        haBase.put("trail_activation_pct", 1.0);
        haBase.put("atr_period", 20);
        haBase.put("swing_left", 20);
        haBase.put("swing_right", 2);
        haBase.put("bos_exit", true);
        smcl(s, "R19", "HA_ACT1_BOSX", haBase);

        // TR0.45/A0.50 base with BOSX
        smcl(s, "R19", "HA_BOSX", haBase, "trail_atr_mult", 0.45, "trail_activation_pct", 0.50);

        // ── A: RSI(2) T70 base (R18 best RSI) + BOSX ──
        Map<String, Object> rsiBase = new HashMap<>(haBase);
        rsiBase.put("entry_mode", "rsi");
        rsiBase.put("rsi_period", 2);
        rsiBase.put("rsi_threshold", 70.0);
        smcl(s, "R19", "RSI70", rsiBase);

        // RSI T70 trail/activation sweep
        for (double tr : new double[]{0.35, 0.40, 0.45, 0.55, 0.60}) {
            smcl(s, "R19", String.format("RSI70_TR%.2f", tr), rsiBase, "trail_atr_mult", tr);
        }
        for (double a : new double[]{0.0, 0.25, 0.50, 0.75, 1.5, 2.0}) {
            smcl(s, "R19", String.format("RSI70_A%.2f", a), rsiBase, "trail_activation_pct", a);
        }

        // RSI T70 ATR period sweep
        for (int atr : new int[]{7, 10, 14, 30}) {
            smcl(s, "R19", "RSI70_ATR" + atr, rsiBase, "atr_period", atr);
        }

        // RSI T70 + ha_exit_bars
        for (int heb : new int[]{1, 2, 3}) {
            smcl(s, "R19", "RSI70_HEB" + heb, rsiBase, "ha_exit_bars", heb);
        }

        // RSI T70 + cooldown
        for (int cd : new int[]{1, 2, 3}) {
            smcl(s, "R19", "RSI70_CD" + cd, rsiBase, "cooldown_bars", cd);
        }

        // RSI T70 + no BOSX (compare with/without BOS exit)
        smcl(s, "R19", "RSI70_NoBOSX", rsiBase, "bos_exit", false);

        // ── B: RSI threshold sweep around 70 ──
        for (double t : new double[]{65, 68, 72, 75, 80}) {
            smcl(s, "R19", String.format("RSI_T%.0f", t), rsiBase, "rsi_threshold", t);
        }

        // ── C: RSI(5) — slightly slower RSI, less noisy ──
        Map<String, Object> rsi5Base = new HashMap<>(rsiBase);
        rsi5Base.put("rsi_period", 5);
        smcl(s, "R19", "RSI5_T70", rsi5Base);
        smcl(s, "R19", "RSI5_T60", rsi5Base, "rsi_threshold", 60.0);
        smcl(s, "R19", "RSI5_T55", rsi5Base, "rsi_threshold", 55.0);
        smcl(s, "R19", "RSI5_T50", rsi5Base, "rsi_threshold", 50.0);

        // ── D: Best combos: RSI70 + trail+activation ──
        for (double tr : new double[]{0.40, 0.45, 0.50}) {
            for (double a : new double[]{0.5, 1.0, 1.5}) {
                if (tr == 0.50 && a == 1.0) continue;
                smcl(s, "R19", String.format("RSI70_TR%.2f_A%.1f", tr, a), rsiBase,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ── E: RSI70 + HEB + trail combos ──
        for (int heb : new int[]{1, 2}) {
            for (double tr : new double[]{0.40, 0.50, 0.60}) {
                smcl(s, "R19", String.format("RSI70_HEB%d_TR%.2f", heb, tr), rsiBase,
                        "ha_exit_bars", heb, "trail_atr_mult", tr);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R20: Breakeven stop + max loss + best combos
    //       New features: max_loss_pct (engine SL), breakeven_pct (trail floor)
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR20(Map<String, Supplier<Strategy>> s) {

        // ── Best HA flip config (R19 #1) ──
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("require_bos", false);
        haBase.put("trend_ema", 0);
        haBase.put("entry_mode", "ha_flip");
        haBase.put("bos_entry", false);
        haBase.put("ha_flip_exit", true);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.50);
        haBase.put("trail_activation_pct", 1.0);
        haBase.put("atr_period", 20);
        haBase.put("swing_left", 20);
        haBase.put("swing_right", 2);
        haBase.put("bos_exit", true);
        smcl(s, "R20", "HA_REF", haBase);  // reference

        // ── Best RSI config (R19 #6) ──
        Map<String, Object> rsiBase = new HashMap<>(haBase);
        rsiBase.put("entry_mode", "rsi");
        rsiBase.put("rsi_period", 2);
        rsiBase.put("rsi_threshold", 72.0);
        smcl(s, "R20", "RSI_REF", rsiBase);  // reference

        // ═══════════════════════════════════════════════════════
        //  A: BREAKEVEN STOP — after X% gain, floor trail at entry
        //     This converts potential losers to breakeven trades → ↑WR, ↑PF
        // ═══════════════════════════════════════════════════════

        // HA flip + breakeven
        for (double be : new double[]{2, 3, 5, 8, 10, 15}) {
            smcl(s, "R20", String.format("HA_BE%.0f", be), haBase, "breakeven_pct", be);
        }

        // RSI + breakeven
        for (double be : new double[]{2, 3, 5, 8, 10, 15}) {
            smcl(s, "R20", String.format("RSI_BE%.0f", be), rsiBase, "breakeven_pct", be);
        }

        // ═══════════════════════════════════════════════════════
        //  B: MAX LOSS STOP — hard % SL (intra-bar via engine)
        //     Caps individual trade losses → ↓MDD, ↑WR on large losses
        // ═══════════════════════════════════════════════════════

        // HA flip + max loss
        for (double ml : new double[]{10, 15, 20, 25, 30}) {
            smcl(s, "R20", String.format("HA_ML%.0f", ml), haBase, "max_loss_pct", ml);
        }

        // RSI + max loss
        for (double ml : new double[]{10, 15, 20, 25, 30}) {
            smcl(s, "R20", String.format("RSI_ML%.0f", ml), rsiBase, "max_loss_pct", ml);
        }

        // ═══════════════════════════════════════════════════════
        //  C: BREAKEVEN + MAX LOSS combos
        //     BE protects gains, ML caps losses → best of both worlds
        // ═══════════════════════════════════════════════════════

        // HA flip + BE + ML combos
        for (double be : new double[]{3, 5, 8}) {
            for (double ml : new double[]{15, 20, 25}) {
                smcl(s, "R20", String.format("HA_BE%.0f_ML%.0f", be, ml), haBase,
                        "breakeven_pct", be, "max_loss_pct", ml);
            }
        }

        // RSI + BE + ML combos
        for (double be : new double[]{3, 5, 8}) {
            for (double ml : new double[]{15, 20, 25}) {
                smcl(s, "R20", String.format("RSI_BE%.0f_ML%.0f", be, ml), rsiBase,
                        "breakeven_pct", be, "max_loss_pct", ml);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D: HEB2 + BE/ML — HEB2 gave best WR (46.9%), add BE/ML to fix MDD
        // ═══════════════════════════════════════════════════════

        // RSI + HEB2 + breakeven
        for (double be : new double[]{3, 5, 8}) {
            smcl(s, "R20", String.format("RSI_HEB2_BE%.0f", be), rsiBase,
                    "ha_exit_bars", 2, "breakeven_pct", be);
        }

        // RSI + HEB2 + max loss
        for (double ml : new double[]{15, 20, 25}) {
            smcl(s, "R20", String.format("RSI_HEB2_ML%.0f", ml), rsiBase,
                    "ha_exit_bars", 2, "max_loss_pct", ml);
        }

        // RSI + HEB2 + BE + ML
        for (double be : new double[]{3, 5}) {
            for (double ml : new double[]{15, 20}) {
                smcl(s, "R20", String.format("RSI_HEB2_BE%.0f_ML%.0f", be, ml), rsiBase,
                        "ha_exit_bars", 2, "breakeven_pct", be, "max_loss_pct", ml);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E: Best BE/ML + trail variations
        // ═══════════════════════════════════════════════════════

        // RSI + BE5 + different trail multipliers
        for (double tr : new double[]{0.40, 0.45, 0.55, 0.60}) {
            smcl(s, "R20", String.format("RSI_BE5_TR%.2f", tr), rsiBase,
                    "breakeven_pct", 5, "trail_atr_mult", tr);
        }

        // RSI + ML20 + different trail multipliers
        for (double tr : new double[]{0.40, 0.45, 0.55, 0.60}) {
            smcl(s, "R20", String.format("RSI_ML20_TR%.2f", tr), rsiBase,
                    "max_loss_pct", 20, "trail_atr_mult", tr);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R21: Volatility filter + RSI floor + HEB2 combos
    //       Score = 90% Calmar + 10% Robustness, COV_CAP=50
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR21(Map<String, Supplier<Strategy>> s) {

        // ── Best HA flip base ──
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("require_bos", false);
        haBase.put("trend_ema", 0);
        haBase.put("entry_mode", "ha_flip");
        haBase.put("bos_entry", false);
        haBase.put("ha_flip_exit", true);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.50);
        haBase.put("trail_activation_pct", 1.0);
        haBase.put("atr_period", 20);
        haBase.put("swing_left", 20);
        haBase.put("swing_right", 2);
        haBase.put("bos_exit", true);
        smcl(s, "R21", "HA_REF", haBase);

        // ── Best RSI base ──
        Map<String, Object> rsiBase = new HashMap<>(haBase);
        rsiBase.put("entry_mode", "rsi");
        rsiBase.put("rsi_period", 2);
        rsiBase.put("rsi_threshold", 72.0);
        smcl(s, "R21", "RSI_REF", rsiBase);

        // ═══════════════════════════════════════════════════════
        //  A: ATR% volatility filter — skip high-volatility entries
        //     ATR/Close*100 > threshold → skip (reduces MDD from volatile stocks)
        // ═══════════════════════════════════════════════════════

        // HA + vol filter
        for (double vol : new double[]{2, 3, 4, 5, 6, 8}) {
            smcl(s, "R21", String.format("HA_VOL%.0f", vol), haBase, "atr_pct_max", vol);
        }

        // RSI + vol filter
        for (double vol : new double[]{2, 3, 4, 5, 6, 8}) {
            smcl(s, "R21", String.format("RSI_VOL%.0f", vol), rsiBase, "atr_pct_max", vol);
        }

        // ═══════════════════════════════════════════════════════
        //  B: RSI floor — only enter when RSI(2) > floor (momentum confirm)
        //     Applied as filter on HA flip entry
        // ═══════════════════════════════════════════════════════

        for (double floor : new double[]{40, 50, 55, 60, 65, 70}) {
            smcl(s, "R21", String.format("HA_RSIF%.0f", floor), haBase, "rsi_floor", floor);
        }

        // ═══════════════════════════════════════════════════════
        //  C: RSI + HEB2 + vol filter (target: reduce MDD while keeping WR/PF)
        // ═══════════════════════════════════════════════════════

        Map<String, Object> rsiHeb2 = new HashMap<>(rsiBase);
        rsiHeb2.put("ha_exit_bars", 2);
        smcl(s, "R21", "RSI_HEB2_REF", rsiHeb2);

        for (double vol : new double[]{3, 4, 5, 6, 8}) {
            smcl(s, "R21", String.format("RSI_HEB2_VOL%.0f", vol), rsiHeb2, "atr_pct_max", vol);
        }

        // ═══════════════════════════════════════════════════════
        //  D: Vol filter + RSI floor combos on HA flip
        // ═══════════════════════════════════════════════════════

        for (double vol : new double[]{3, 5}) {
            for (double floor : new double[]{50, 60}) {
                smcl(s, "R21", String.format("HA_VOL%.0f_RSIF%.0f", vol, floor), haBase,
                        "atr_pct_max", vol, "rsi_floor", floor);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E: Vol filter + different trail settings on RSI
        // ═══════════════════════════════════════════════════════

        for (double vol : new double[]{4, 5}) {
            for (double tr : new double[]{0.40, 0.45, 0.55}) {
                smcl(s, "R21", String.format("RSI_VOL%.0f_TR%.2f", vol, tr), rsiBase,
                        "atr_pct_max", vol, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F: HEB2 + vol filter + trail combos
        // ═══════════════════════════════════════════════════════

        for (double vol : new double[]{4, 5}) {
            for (double tr : new double[]{0.40, 0.50, 0.60}) {
                smcl(s, "R21", String.format("RSI_HEB2_VOL%.0f_TR%.2f", vol, tr), rsiHeb2,
                        "atr_pct_max", vol, "trail_atr_mult", tr);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R22: Targeted combos to improve ALL metrics simultaneously
    //       Previous best: WR 43.5%, MDD -45.65%, CAGR +11.80%, PF 1.32
    //       Goal: Score↑ WR↑ MDD↓ CAGR↑ PF↑
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR22(Map<String, Supplier<Strategy>> s) {

        // ── Common base ──
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        base.put("bos_exit", true);

        // ── Reference: previous best (HA flip, ACT1) ──
        Map<String, Object> haRef = new HashMap<>(base);
        haRef.put("entry_mode", "ha_flip");
        haRef.put("trail_atr_mult", 0.50);
        haRef.put("trail_activation_pct", 1.0);
        smcl(s, "R22", "HA_REF", haRef);

        // ═══════════════════════════════════════════════════════
        //  A: RSI(2)≥72 entry — PF 1.37, WR 44.2% (both ↑)
        //     Fine-tune trail to also get MDD↓ and CAGR↑
        // ═══════════════════════════════════════════════════════

        Map<String, Object> rsi72 = new HashMap<>(base);
        rsi72.put("entry_mode", "rsi");
        rsi72.put("rsi_period", 2);
        rsi72.put("rsi_threshold", 72.0);
        rsi72.put("trail_atr_mult", 0.50);
        rsi72.put("trail_activation_pct", 1.0);
        smcl(s, "R22", "RSI72", rsi72);

        // RSI72 + lower trail for MDD↓
        for (double tr : new double[]{0.35, 0.40, 0.42, 0.45, 0.48}) {
            smcl(s, "R22", String.format("RSI72_TR%.2f", tr), rsi72, "trail_atr_mult", tr);
        }

        // RSI72 + lower activation for MDD↓
        for (double a : new double[]{0.0, 0.25, 0.50, 0.75}) {
            smcl(s, "R22", String.format("RSI72_A%.2f", a), rsi72, "trail_activation_pct", a);
        }

        // RSI72 + combined trail+activation sweet spot
        for (double tr : new double[]{0.42, 0.45, 0.48}) {
            for (double a : new double[]{0.50, 0.75, 1.0}) {
                if (tr == 0.48 && a == 1.0) continue;
                smcl(s, "R22", String.format("RSI72_TR%.2f_A%.2f", tr, a), rsi72,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B: HA flip + RSIF (RSI floor) — MDD↓ and PF↑
        //     HA_RSIF50 gave MDD -44.18, PF 1.35 but CAGR 11.10 (↓)
        //     Try RSIF 30-45 to balance CAGR retention vs MDD reduction
        // ═══════════════════════════════════════════════════════

        Map<String, Object> haF = new HashMap<>(haRef);
        for (double floor : new double[]{30, 35, 40, 45, 50}) {
            smcl(s, "R22", String.format("HA_RSIF%.0f", floor), haF, "rsi_floor", floor);
        }

        // HA_RSIF + lower trail for extra MDD reduction
        for (double floor : new double[]{35, 40, 45}) {
            for (double tr : new double[]{0.45, 0.48}) {
                smcl(s, "R22", String.format("HA_RSIF%.0f_TR%.2f", floor, tr), haF,
                        "rsi_floor", floor, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C: RSI72 + HEB1 — small WR boost
        // ═══════════════════════════════════════════════════════

        smcl(s, "R22", "RSI72_HEB1", rsi72, "ha_exit_bars", 1);
        smcl(s, "R22", "RSI72_HEB1_TR0.45", rsi72, "ha_exit_bars", 1, "trail_atr_mult", 0.45);
        smcl(s, "R22", "RSI72_HEB1_TR0.48", rsi72, "ha_exit_bars", 1, "trail_atr_mult", 0.48);
        smcl(s, "R22", "RSI72_HEB1_A0.50", rsi72, "ha_exit_bars", 1, "trail_activation_pct", 0.50);

        // ═══════════════════════════════════════════════════════
        //  D: RSI different thresholds around 72
        // ═══════════════════════════════════════════════════════

        for (double t : new double[]{68, 70, 74, 75, 78}) {
            smcl(s, "R22", String.format("RSI_T%.0f", t), rsi72, "rsi_threshold", t);
        }

        // ═══════════════════════════════════════════════════════
        //  E: RSI72 + mild VOL filter (VOL8-12, less aggressive)
        // ═══════════════════════════════════════════════════════

        for (double vol : new double[]{8, 10, 12, 15}) {
            smcl(s, "R22", String.format("RSI72_VOL%.0f", vol), rsi72, "atr_pct_max", vol);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R23: Take-profit + stepped combos
    //       New feature: tp_pct — engine-level intra-bar TP
    //       Previous best: RSI72_HEB1_A0.50 → WR 44.5%, MDD -45.04%, CAGR +12.03%, PF 1.38
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR28(Map<String, Supplier<Strategy>> s) {

        // ── R27 findings — TP1.5 dominates ALL metrics vs TP9_REF:
        // TP1.5:    CAGR +131.58%, MDD -30.24%, PF 1.53, WR 56.8%
        // RSI4_TP2: CAGR +92.54%,  MDD -27.96%, PF 1.52, WR 56.5% (best MDD)
        // TP2_HEB2: CAGR +134.42%, MDD -36.42%, PF 1.47, WR 59.8% (best WR)
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "rsi");
        base.put("rsi_period", 2);
        base.put("rsi_threshold", 72.0);
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("ha_exit_bars", 1);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 0.50);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        base.put("bos_exit", true);

        // References
        smcl(s, "R28", "TP1.5_REF", base, "tp_pct", 1.5);
        smcl(s, "R28", "TP9_REF", base, "tp_pct", 9.0);

        // ═══════════════════════════════════════════════════════
        //  A: Fine-tune TP around 1.5% (0.1 step)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{0.5, 0.75, 1.0, 1.25, 1.75, 2.0}) {
            smcl(s, "R28", String.format("TP%.2f", tp), base, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  B: TP1.5 + trail combos (tighter trail = lower MDD)
        // ═══════════════════════════════════════════════════════

        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40, 0.60}) {
            smcl(s, "R28", String.format("TR%.2f", tr), base, "tp_pct", 1.5, "trail_atr_mult", tr);
        }

        // ═══════════════════════════════════════════════════════
        //  C: TP1.5 + HEB variations
        // ═══════════════════════════════════════════════════════

        smcl(s, "R28", "HEB0", base, "tp_pct", 1.5, "ha_exit_bars", 0);
        smcl(s, "R28", "HEB2", base, "tp_pct", 1.5, "ha_exit_bars", 2);
        smcl(s, "R28", "noHA", base, "tp_pct", 1.5, "ha_flip_exit", false);

        // ═══════════════════════════════════════════════════════
        //  D: TP1.5 + activation combos
        // ═══════════════════════════════════════════════════════

        for (double a : new double[]{0.0, 0.25, 1.0}) {
            smcl(s, "R28", String.format("A%.2f", a), base, "tp_pct", 1.5, "trail_activation_pct", a);
        }

        // ═══════════════════════════════════════════════════════
        //  E: RSI(4) + TP1-2 fine-tune (best MDD direction)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{1.0, 1.25, 1.5, 1.75, 2.0, 2.5}) {
            smcl(s, "R28", String.format("RSI4_TP%.2f", tp), base, "rsi_period", 4, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  F: RSI(3) + TP1-2 fine-tune
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{1.0, 1.25, 1.5, 1.75, 2.0}) {
            smcl(s, "R28", String.format("RSI3_TP%.2f", tp), base, "rsi_period", 3, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  G: Combined best: TP1.5 + TR0.30 + HEB2
        // ═══════════════════════════════════════════════════════

        smcl(s, "R28", "TP1.5_TR0.30_HEB2", base, "tp_pct", 1.5, "trail_atr_mult", 0.30, "ha_exit_bars", 2);
        smcl(s, "R28", "TP1.5_TR0.35_HEB2", base, "tp_pct", 1.5, "trail_atr_mult", 0.35, "ha_exit_bars", 2);

        // ═══════════════════════════════════════════════════════
        //  H: RSI(4) + TP1.5 + combos
        // ═══════════════════════════════════════════════════════

        smcl(s, "R28", "RSI4_TP1.5_HEB2", base, "rsi_period", 4, "tp_pct", 1.5, "ha_exit_bars", 2);
        smcl(s, "R28", "RSI4_TP1.5_TR0.35", base, "rsi_period", 4, "tp_pct", 1.5, "trail_atr_mult", 0.35);
        smcl(s, "R28", "RSI4_TP2_HEB2", base, "rsi_period", 4, "tp_pct", 2.0, "ha_exit_bars", 2);
    }

    // ══════════════════════════════════════════════════════════════
    //  R29 — Risk-controlled variants of top R11_W:EB3_TR3
    //
    //  Goal: Push composite score above 90 by slashing MDD via:
    //    - trend_ema (regime filter)
    //    - sl_atr_mult (hard stop)
    //    - adx_filter (only trending markets)
    //    - regime filter
    //    - volume filter
    //
    //  Current top R11_W:EB3_TR3 metrics on 572 stocks:
    //    CAGR +9.56%, MDD -53.31%, PF 1.84, Score 73.3
    //
    //  Target to hit score 90+:
    //    CAGR ≥ 8%, MDD ≤ -20%, PF ≥ 2.5
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR29(Map<String, Supplier<Strategy>> s) {

        // Base mirrors R11_W:EB3_TR3 (top current strategy)
        Map<String, Object> base = new HashMap<>();
        base.put("entry_mode", "flip");
        base.put("ha_flip_exit", false);
        base.put("trailing_stop", true);
        base.put("sl_atr_mult", 0.0);
        base.put("tp_atr_mult", 0.0);
        base.put("timeframe", "1W");
        base.put("exit_bearish_bars", 3);
        base.put("trail_atr_mult", 3.0);
        base.put("trail_activation_pct", 3.0);
        base.put("atr_period", 14);

        // Reference (matches R11_W:EB3_TR3 exactly)
        ha(s, "R29", "REF", base);

        // ═══════════════════════════════════════════════════════
        //  A: Trend EMA filter (200, 100, 50)
        //     Only enter long when price is above EMA — kills downtrend trades
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{50, 100, 200}) {
            ha(s, "R29", "EMA" + ema, base, "trend_ema", ema);
        }

        // ═══════════════════════════════════════════════════════
        //  B: Hard stop loss (ATR-based) — caps single-trade loss
        // ═══════════════════════════════════════════════════════
        for (double sl : new double[]{1.5, 2.0, 2.5, 3.0}) {
            ha(s, "R29", String.format("SL%.1f", sl), base, "sl_atr_mult", sl);
        }

        // ═══════════════════════════════════════════════════════
        //  C: ADX filter — only enter in trending markets
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{20, 25, 30}) {
            ha(s, "R29", String.format("ADX%.0f", adx), base,
                    "adx_filter", true, "adx_threshold", adx, "adx_period", 14);
        }

        // ═══════════════════════════════════════════════════════
        //  D: Trend EMA + SL combos (defense in depth)
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{100, 200}) {
            for (double sl : new double[]{1.5, 2.0, 2.5}) {
                ha(s, "R29", String.format("EMA%d_SL%.1f", ema, sl), base,
                        "trend_ema", ema, "sl_atr_mult", sl);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E: Trend EMA + ADX combos
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{100, 200}) {
            for (double adx : new double[]{20, 25}) {
                ha(s, "R29", String.format("EMA%d_ADX%.0f", ema, adx), base,
                        "trend_ema", ema, "adx_filter", true, "adx_threshold", adx);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F: Triple filter — EMA + SL + ADX
        // ═══════════════════════════════════════════════════════
        ha(s, "R29", "EMA200_SL2_ADX25", base,
                "trend_ema", 200, "sl_atr_mult", 2.0,
                "adx_filter", true, "adx_threshold", 25.0);
        ha(s, "R29", "EMA200_SL1.5_ADX20", base,
                "trend_ema", 200, "sl_atr_mult", 1.5,
                "adx_filter", true, "adx_threshold", 20.0);
        ha(s, "R29", "EMA100_SL2_ADX20", base,
                "trend_ema", 100, "sl_atr_mult", 2.0,
                "adx_filter", true, "adx_threshold", 20.0);

        // ═══════════════════════════════════════════════════════
        //  G: Tighter trail variants (lock in profits faster)
        // ═══════════════════════════════════════════════════════
        for (double tr : new double[]{1.0, 1.5, 2.0}) {
            ha(s, "R29", String.format("EMA200_TR%.1f", tr), base,
                    "trend_ema", 200, "trail_atr_mult", tr);
        }

        // ═══════════════════════════════════════════════════════
        //  H: TP take-profit variants (cap winners)
        // ═══════════════════════════════════════════════════════
        for (double tp : new double[]{2.0, 3.0, 5.0}) {
            ha(s, "R29", String.format("EMA200_TP%.0f", tp), base,
                    "trend_ema", 200, "tp_atr_mult", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  I: Regime filter — only trade when >X% of lookback bars bullish
        // ═══════════════════════════════════════════════════════
        for (double rt : new double[]{0.55, 0.65, 0.75}) {
            ha(s, "R29", String.format("REG%.2f", rt), base,
                    "regime_window", 10, "regime_threshold", rt);
        }

        // ═══════════════════════════════════════════════════════
        //  J: Volume filter — confirm volume on entry
        // ═══════════════════════════════════════════════════════
        ha(s, "R29", "VOL1.5", base, "volume_filter", true, "vol_mult", 1.5);
        ha(s, "R29", "VOL2.0", base, "volume_filter", true, "vol_mult", 2.0);
        ha(s, "R29", "EMA200_VOL1.5", base, "trend_ema", 200, "volume_filter", true, "vol_mult", 1.5);
    }

    // ══════════════════════════════════════════════════════════════
    //  R30 — Research-validated swing trading strategies for NSE
    //
    //  Strategies proven in academic literature & practitioner backtests
    //  on Indian markets (Nifty/Nifty 500, 2015-2026):
    //
    //  1. Donchian breakout (Turtle): 20/55-day high → 10/20-day exit
    //     Reference: QuantInsti backtest 2023 — Nifty 50, CAGR 19.4%, MDD 12.7%
    //  2. Momentum (Jegadeesh-Titman): 6-12 month ROC ranking
    //     Reference: SSRN paper on India, CAGR 22%, Sharpe 0.98
    //  3. Stan Weinstein Stage 2: price > EMA50 > EMA200, all rising
    //  4. 52-week high breakout (Minervini-style)
    //  5. Pullback to EMA in confirmed uptrend
    //  6. SuperTrend ATR trend-following
    //  7. Chandelier Exit (let winners run, cut losers short → high PF)
    //  8. Mean reversion (RSI(2)) ONLY in uptrend (long-only filter)
    //
    //  Target: Score ≥ 95 on Nifty 500 universe (572 stocks)
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR30(Map<String, Supplier<Strategy>> s) {

        // ─────────────────────────────────────────────────────────
        //  A. DONCHIAN BREAKOUT (Turtle Trading System)
        //     Buy 20-day high; sell 10-day low. Trend filter + ATR stop.
        // ─────────────────────────────────────────────────────────
        Map<String, Object> dcBase = new HashMap<>();
        dcBase.put("strategy_type", "donchian");
        dcBase.put("atr_period", 20);
        dcBase.put("sl_atr_mult", 0.0);
        dcBase.put("tp_atr_mult", 0.0);

        // Standard Turtle (no filter)
        quant(s, "R30", "DON20", dcBase, "donchian_entry", 20, "donchian_exit", 10);
        quant(s, "R30", "DON55", dcBase, "donchian_entry", 55, "donchian_exit", 20);

        // Turtle + EMA200 trend filter (only buy when above 200 EMA)
        quant(s, "R30", "DON20_T1", dcBase,
                "donchian_entry", 20, "donchian_exit", 10, "trend_ema", 200);
        quant(s, "R30", "DON55_T1", dcBase,
                "donchian_entry", 55, "donchian_exit", 20, "trend_ema", 200);

        // 52-week high breakout (Minervini-style)
        quant(s, "R30", "DON252_T1", dcBase,
                "donchian_entry", 252, "donchian_exit", 50, "trend_ema", 200);

        // Turtle + EMA200 + tight ATR stop (cut losers)
        for (double sl : new double[]{2.0, 3.0}) {
            quant(s, "R30", String.format("DON20_T1_SL%.0f", sl), dcBase,
                    "donchian_entry", 20, "donchian_exit", 10,
                    "trend_ema", 200, "sl_atr_mult", sl);
            quant(s, "R30", String.format("DON55_T1_SL%.0f", sl), dcBase,
                    "donchian_entry", 55, "donchian_exit", 20,
                    "trend_ema", 200, "sl_atr_mult", sl);
        }

        // ─────────────────────────────────────────────────────────
        //  B. MOMENTUM (Jegadeesh-Titman / 52-week high)
        //     Hold stocks with top ROC ranking
        // ─────────────────────────────────────────────────────────
        Map<String, Object> momBase = new HashMap<>();
        momBase.put("strategy_type", "dual_mom");
        momBase.put("atr_period", 20);
        momBase.put("sl_atr_mult", 0.0);
        momBase.put("tp_atr_mult", 0.0);

        // 3-month, 6-month, 12-month momentum
        for (int lb : new int[]{63, 126, 252}) {
            for (int ema : new int[]{5, 10, 20}) {
                quant(s, "R30", String.format("MOM%d_E%d", lb, ema), momBase,
                        "mom_lookback", lb, "mom_ema", ema);
            }
        }

        // Momentum + EMA200 trend filter
        quant(s, "R30", "MOM126_T1", momBase,
                "mom_lookback", 126, "mom_ema", 10, "trend_ema", 200);
        quant(s, "R30", "MOM252_T1", momBase,
                "mom_lookback", 252, "mom_ema", 10, "trend_ema", 200);

        // Momentum + ATR stop
        for (int lb : new int[]{126, 252}) {
            for (double sl : new double[]{2.0, 3.0}) {
                quant(s, "R30", String.format("MOM%d_SL%.0f", lb, sl), momBase,
                        "mom_lookback", lb, "mom_ema", 10, "sl_atr_mult", sl);
            }
        }

        // ─────────────────────────────────────────────────────────
        //  C. CHANDELIER EXIT (let winners run, cut losers short)
        //     High profit-factor strategy by design
        // ─────────────────────────────────────────────────────────
        Map<String, Object> chBase = new HashMap<>();
        chBase.put("strategy_type", "chandelier");
        chBase.put("atr_period", 20);
        chBase.put("sl_atr_mult", 0.0);
        chBase.put("tp_atr_mult", 0.0);

        // Chandelier with various periods/multipliers
        for (int p : new int[]{14, 22, 30}) {
            for (double m : new double[]{2.0, 3.0, 4.0}) {
                quant(s, "R30", String.format("CH%d_M%.0f", p, m), chBase,
                        "chandelier_period", p, "chandelier_mult", m);
            }
        }

        // Chandelier + EMA200 trend filter
        quant(s, "R30", "CH22_M3_T1", chBase,
                "chandelier_period", 22, "chandelier_mult", 3.0, "trend_ema", 200);
        quant(s, "R30", "CH22_M2_T1", chBase,
                "chandelier_period", 22, "chandelier_mult", 2.0, "trend_ema", 200);
        quant(s, "R30", "CH30_M3_T1", chBase,
                "chandelier_period", 30, "chandelier_mult", 3.0, "trend_ema", 200);
        quant(s, "R30", "CH22_M3_T50", chBase,
                "chandelier_period", 22, "chandelier_mult", 3.0, "trend_ema", 50);

        // ─────────────────────────────────────────────────────────
        //  D. MEAN REVERSION IN TREND (RSI(2) + EMA200)
        //     Buy oversold pullbacks in confirmed uptrend
        // ─────────────────────────────────────────────────────────
        Map<String, Object> mrBase = new HashMap<>();
        mrBase.put("strategy_type", "mean_revert");
        mrBase.put("atr_period", 20);

        // Various RSI periods & entry thresholds
        for (int rp : new int[]{2, 3, 4}) {
            for (double re : new double[]{10, 20, 30}) {
                quant(s, "R30", String.format("MR_R%d_E%.0f", rp, re), mrBase,
                        "mr_rsi_period", rp, "mr_rsi_entry", re, "mr_trend_ema", 200);
            }
        }

        // ─────────────────────────────────────────────────────────
        //  E. VOLATILITY BREAKOUT (Larry Williams style)
        //     Buy when range exceeds prior day's range × multiplier
        // ─────────────────────────────────────────────────────────
        Map<String, Object> vbBase = new HashMap<>();
        vbBase.put("strategy_type", "vol_breakout");
        vbBase.put("atr_period", 20);

        // VB + EMA200 trend filter
        for (double m : new double[]{0.4, 0.6, 0.8}) {
            for (int lb : new int[]{10, 20}) {
                quant(s, "R30", String.format("VB%.1f_L%d_T1", m, lb), vbBase,
                        "vb_mult", m, "vb_lookback", lb, "trend_ema", 200);
            }
        }

        // ─────────────────────────────────────────────────────────
        //  F. EMA PULLBACK (Buy support in uptrend)
        //     SMCLite pullback mode with EMA200 trend filter
        // ─────────────────────────────────────────────────────────
        Map<String, Object> pbBase = new HashMap<>();
        pbBase.put("require_bos", false);
        pbBase.put("trend_ema", 200);
        pbBase.put("entry_mode", "pullback");
        pbBase.put("ha_flip_exit", true);
        pbBase.put("ha_exit_bars", 1);
        pbBase.put("trailing_stop", true);
        pbBase.put("atr_period", 14);
        pbBase.put("swing_left", 5);
        pbBase.put("swing_right", 2);

        // Pullback to various EMAs
        for (int ema : new int[]{10, 20, 50}) {
            smcl(s, "R30", String.format("PB_E%d", ema), pbBase, "entry_ema", ema);
            smcl(s, "R30", String.format("PB_E%d_SL2", ema), pbBase,
                    "entry_ema", ema, "sl_atr_mult", 2.0);
            smcl(s, "R30", String.format("PB_E%d_TR1", ema), pbBase,
                    "entry_ema", ema, "trail_atr_mult", 1.0, "trail_activation_pct", 2.0);
        }

        // ─────────────────────────────────────────────────────────
        //  G. HEIKIN ASHI + STRICT RISK CONTROLS
        //     HA flip with EMA200 + ATR stop + tight trail
        //     Target: high Sharpe + high PF
        // ─────────────────────────────────────────────────────────
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("atr_period", 14);

        // HA + trend filter + tight trail
        for (double tr : new double[]{0.5, 1.0, 1.5, 2.0}) {
            for (double a : new double[]{2.0, 3.0}) {
                ha(s, "R30", String.format("HA_T1_TR%.1f_A%.0f", tr, a), haBase,
                        "trend_ema", 200, "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // HA + trend filter + ATR stop
        for (double sl : new double[]{2.0, 3.0}) {
            ha(s, "R30", String.format("HA_T1_SL%.1f", sl), haBase,
                    "trend_ema", 200, "sl_atr_mult", sl,
                    "trail_atr_mult", 1.0, "trail_activation_pct", 3.0);
        }

        // HA + trend + ADX filter (only strong trends)
        ha(s, "R30", "HA_T1_ADX25", haBase,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 25.0);
        ha(s, "R30", "HA_T1_ADX25_SL2", haBase,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 25.0,
                "sl_atr_mult", 2.0);

        // HA + strong candle + trend filter
        ha(s, "R30", "HA_T1_STRONG", haBase,
                "trend_ema", 200, "strong_candle", true);

        // ─────────────────────────────────────────────────────────
        //  H. SQUEEZE BREAKOUT (Bollinger inside Keltner — volatility contraction)
        //     Proxy for VCP (Minervini Volatility Contraction Pattern)
        // ─────────────────────────────────────────────────────────
        Map<String, Object> sqBase = new HashMap<>();
        sqBase.put("strategy_type", "squeeze");
        sqBase.put("atr_period", 20);

        for (int len : new int[]{15, 20, 30}) {
            for (double km : new double[]{1.5, 2.0}) {
                quant(s, "R30", String.format("SQ%d_K%.1f_T1", len, km), sqBase,
                        "kc_len", len, "kc_mult", km, "bb_len", len, "trend_ema", 200);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R31 — Improvements to SMCL_R4:BOSX_SW20_3 (current #1 strategy)
    //
    //  Base config (current top): CAGR +10.83%, MDD -40.50%, PF 1.22, WR 41.5%
    //
    //  Failure analysis (per-asset breakdown on 572 stocks):
    //    - 63 stocks lose money (e.g., YESBANK -61.5%, IDEA -42%, ZEEL -36%)
    //    - 137 stocks have MDD < -50%
    //    - 12 stocks have MDD < -70% (catastrophic)
    //    - Strategy enters HA-flip even in confirmed downtrends → big losses
    //    - No initial stop loss → losses can balloon before trail activates
    //    - Trail activates only at +3% profit → early losses unprotected
    //
    //  Improvements applied:
    //    1. EMA200 trend filter (skip downtrending stocks like YESBANK/IDEA)
    //    2. Initial ATR stop loss (cap single-trade losses)
    //    3. Earlier trail activation (protect profits sooner)
    //    4. ADX filter (avoid choppy sideways markets)
    //    5. RSI floor (avoid catching falling knives)
    //    6. Combo filters (defense in depth)
    //
    //  Target: cut MDD by 30-50%, maintain or boost CAGR
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR31(Map<String, Supplier<Strategy>> s) {

        // Base: SMCL_R4:BOSX_SW20_3 config (the current #1)
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);             // will override per variant
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.5);
        base.put("trail_activation_pct", 3.0);
        base.put("bos_exit", true);
        base.put("swing_left", 20);
        base.put("swing_right", 3);
        base.put("atr_period", 14);

        // ─────────────────────────────────────────────────────────
        //  A. TREND FILTER (skip downtrending stocks)
        //     Most critical fix — YESBANK/IDEA-type failures
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T50", base, "trend_ema", 50);
        smcl(s, "R31", "T100", base, "trend_ema", 100);
        smcl(s, "R31", "T200", base, "trend_ema", 200);

        // ─────────────────────────────────────────────────────────
        //  B. INITIAL STOP LOSS (cap single-trade losses)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "SL2", base, "sl_atr_mult", 2.0);
        smcl(s, "R31", "SL3", base, "sl_atr_mult", 3.0);
        smcl(s, "R31", "SL4", base, "sl_atr_mult", 4.0);

        // ─────────────────────────────────────────────────────────
        //  C. EARLIER TRAIL ACTIVATION (protect profits sooner)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "A0.5", base, "trail_activation_pct", 0.5);
        smcl(s, "R31", "A1.0", base, "trail_activation_pct", 1.0);
        smcl(s, "R31", "A1.5", base, "trail_activation_pct", 1.5);
        smcl(s, "R31", "A2.0", base, "trail_activation_pct", 2.0);

        // ─────────────────────────────────────────────────────────
        //  D. TIGHTER TRAIL (exit faster on reversals)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "TR0.3", base, "trail_atr_mult", 0.3);
        smcl(s, "R31", "TR0.4", base, "trail_atr_mult", 0.4);
        smcl(s, "R31", "TR0.7", base, "trail_atr_mult", 0.7);

        // ─────────────────────────────────────────────────────────
        //  E. ADX FILTER (only trending markets)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "ADX15", base, "adx_filter", true, "adx_threshold", 15.0);
        smcl(s, "R31", "ADX20", base, "adx_filter", true, "adx_threshold", 20.0);
        smcl(s, "R31", "ADX25", base, "adx_filter", true, "adx_threshold", 25.0);

        // ─────────────────────────────────────────────────────────
        //  F. RSI FLOOR (avoid catching falling knives)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "RSI40", base, "rsi_floor", 40.0);
        smcl(s, "R31", "RSI50", base, "rsi_floor", 50.0);

        // ─────────────────────────────────────────────────────────
        //  G. COMBO: TREND + STOP LOSS (defense in depth)
        //     Most promising — addresses both top failure modes
        // ─────────────────────────────────────────────────────────
        for (int ema : new int[]{100, 200}) {
            for (double sl : new double[]{2.0, 3.0, 4.0}) {
                smcl(s, "R31", String.format("T%d_SL%.0f", ema, sl), base,
                        "trend_ema", ema, "sl_atr_mult", sl);
            }
        }

        // ─────────────────────────────────────────────────────────
        //  H. COMBO: TREND + EARLY TRAIL ACTIVATION
        // ─────────────────────────────────────────────────────────
        for (int ema : new int[]{100, 200}) {
            for (double a : new double[]{0.5, 1.0, 1.5}) {
                smcl(s, "R31", String.format("T%d_A%.1f", ema, a), base,
                        "trend_ema", ema, "trail_activation_pct", a);
            }
        }

        // ─────────────────────────────────────────────────────────
        //  I. COMBO: TREND + STOP + EARLY TRAIL
        //     Triple defense — expect MDD reduction
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T200_SL2_A1", base,
                "trend_ema", 200, "sl_atr_mult", 2.0, "trail_activation_pct", 1.0);
        smcl(s, "R31", "T200_SL3_A1", base,
                "trend_ema", 200, "sl_atr_mult", 3.0, "trail_activation_pct", 1.0);
        smcl(s, "R31", "T200_SL2_A0.5", base,
                "trend_ema", 200, "sl_atr_mult", 2.0, "trail_activation_pct", 0.5);
        smcl(s, "R31", "T100_SL2_A1", base,
                "trend_ema", 100, "sl_atr_mult", 2.0, "trail_activation_pct", 1.0);
        smcl(s, "R31", "T100_SL3_A1", base,
                "trend_ema", 100, "sl_atr_mult", 3.0, "trail_activation_pct", 1.0);

        // ─────────────────────────────────────────────────────────
        //  J. COMBO: TREND + TIGHTER TRAIL + EARLIER ACTIVATION
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T200_TR0.3_A1", base,
                "trend_ema", 200, "trail_atr_mult", 0.3, "trail_activation_pct", 1.0);
        smcl(s, "R31", "T200_TR0.4_A1", base,
                "trend_ema", 200, "trail_atr_mult", 0.4, "trail_activation_pct", 1.0);
        smcl(s, "R31", "T200_TR0.3_A0.5", base,
                "trend_ema", 200, "trail_atr_mult", 0.3, "trail_activation_pct", 0.5);

        // ─────────────────────────────────────────────────────────
        //  K. COMBO: TREND + ADX + STOP
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T200_ADX20", base,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 20.0);
        smcl(s, "R31", "T200_ADX25", base,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 25.0);
        smcl(s, "R31", "T200_ADX20_SL2", base,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 20.0, "sl_atr_mult", 2.0);
        smcl(s, "R31", "T200_ADX20_SL3", base,
                "trend_ema", 200, "adx_filter", true, "adx_threshold", 20.0, "sl_atr_mult", 3.0);

        // ─────────────────────────────────────────────────────────
        //  L. COMBO: TREND + RSI FLOOR + STOP (best for falling knives)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T200_RSI50_SL2", base,
                "trend_ema", 200, "rsi_floor", 50.0, "sl_atr_mult", 2.0);
        smcl(s, "R31", "T200_RSI40_SL3", base,
                "trend_ema", 200, "rsi_floor", 40.0, "sl_atr_mult", 3.0);

        // ─────────────────────────────────────────────────────────
        //  M. ULTIMATE: All filters combined
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "ULTRA_T200", base,
                "trend_ema", 200, "sl_atr_mult", 2.0, "trail_activation_pct", 1.0,
                "trail_atr_mult", 0.4, "rsi_floor", 40.0);
        smcl(s, "R31", "ULTRA_T100", base,
                "trend_ema", 100, "sl_atr_mult", 2.0, "trail_activation_pct", 1.0,
                "trail_atr_mult", 0.4, "rsi_floor", 40.0);
        smcl(s, "R31", "ULTRA_ADX20", base,
                "trend_ema", 200, "sl_atr_mult", 2.0, "trail_activation_pct", 1.0,
                "trail_atr_mult", 0.4, "adx_filter", true, "adx_threshold", 20.0);

        // ─────────────────────────────────────────────────────────
        //  N. EMA200 + SMALLER SWING (faster reaction)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R31", "T200_SW10_3", base,
                "trend_ema", 200, "swing_left", 10, "swing_right", 3);
        smcl(s, "R31", "T200_SW15_3", base,
                "trend_ema", 200, "swing_left", 15, "swing_right", 3);
        smcl(s, "R31", "T200_SW30_3", base,
                "trend_ema", 200, "swing_left", 30, "swing_right", 3);
    }

    // ══════════════════════════════════════════════════════════════
    //  R32 — Quality Trade Filter (research-backed multi-confirmation)
    //
    //  Goal: Reduce false trades, increase CAGR, decrease MDD
    //
    //  Research basis (Mark Minervini SEPA + Stan Weinstein Stage 2):
    //    - Minervini Trend Template: close > EMA50 > EMA150 > EMA200, all rising
    //    - Stage 2: stock above rising 30-week MA (~200 day)
    //    - Within 25% of 52-week high (current strength, not recovery)
    //    - Volume confirmation (1.5-2x average) for breakouts
    //    - These eliminate ~95% of stocks → fewer, higher quality trades
    //
    //  Variant philosophies:
    //    A. HOLD-style: Remove HA flip exit (stops whipsaws), wider trail
    //    B. RB (require_bos): Only enter in confirmed bullish BOS regime
    //    C. STAGE2: Minervini multi-EMA alignment
    //    D. HQ: All filters combined
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR32(Map<String, Supplier<Strategy>> s) {

        // Base: SMCL_R4:BOSX_SW20_3 (current #1)
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "ha_flip");
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.5);
        base.put("trail_activation_pct", 3.0);
        base.put("bos_exit", true);
        base.put("swing_left", 20);
        base.put("swing_right", 3);
        base.put("atr_period", 14);

        // ─────────────────────────────────────────────────────────
        //  A. HOLD-STYLE: Remove HA flip exit (stops whipsaws)
        //     Hypothesis: HA flips are noisy → many false exits
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "HOLD", base, "ha_flip_exit", false);
        smcl(s, "R32", "HOLD_TR1.0", base, "ha_flip_exit", false,
                "trail_atr_mult", 1.0, "trail_activation_pct", 5.0);
        smcl(s, "R32", "HOLD_TR1.5", base, "ha_flip_exit", false,
                "trail_atr_mult", 1.5, "trail_activation_pct", 5.0);
        smcl(s, "R32", "HOLD_TR2.0", base, "ha_flip_exit", false,
                "trail_atr_mult", 2.0, "trail_activation_pct", 5.0);

        // ─────────────────────────────────────────────────────────
        //  B. REQUIRE_BOS: Only enter in confirmed bullish structure
        //     Hypothesis: BOS confirms uptrend, filters out downtrend whipsaws
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "RB", base, "require_bos", true);
        smcl(s, "R32", "RB_HOLD", base, "require_bos", true, "ha_flip_exit", false);
        smcl(s, "R32", "RB_TR1.5", base, "require_bos", true,
                "trail_atr_mult", 1.5, "trail_activation_pct", 5.0);

        // ─────────────────────────────────────────────────────────
        //  C. MINERVINI STAGE 2 (multi-EMA alignment)
        //     Hypothesis: 95% of stocks fail this → only top 5% taken
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "STAGE2", base, "stage2_filter", true);
        smcl(s, "R32", "STAGE2_HOLD", base, "stage2_filter", true, "ha_flip_exit", false);
        smcl(s, "R32", "STAGE2_TR1.5", base, "stage2_filter", true,
                "trail_atr_mult", 1.5, "trail_activation_pct", 5.0);

        // ─────────────────────────────────────────────────────────
        //  D. 52-WEEK HIGH PROXIMITY (within X% of high)
        //     Hypothesis: leaders trade near highs, not recovery plays
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "H25", base, "max_pct_from_high", 25.0);
        smcl(s, "R32", "H15", base, "max_pct_from_high", 15.0);
        smcl(s, "R32", "H35", base, "max_pct_from_high", 35.0);

        // ─────────────────────────────────────────────────────────
        //  E. EMA RISING (trend slope filter)
        //     Hypothesis: trend must be rising, not just above EMA
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "T200_SLP21", base, "trend_ema", 200, "ema_slope_bars", 21);
        smcl(s, "R32", "T200_SLP63", base, "trend_ema", 200, "ema_slope_bars", 63);

        // ─────────────────────────────────────────────────────────
        //  F. COMBOS: Stage 2 + structure + wide exit
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "STAGE2_RB", base, "stage2_filter", true, "require_bos", true);
        smcl(s, "R32", "STAGE2_H25", base, "stage2_filter", true, "max_pct_from_high", 25.0);
        smcl(s, "R32", "STAGE2_H25_HOLD", base, "stage2_filter", true,
                "max_pct_from_high", 25.0, "ha_flip_exit", false);

        // ─────────────────────────────────────────────────────────
        //  G. HIGH QUALITY: Multi-confirmation (Minervini SEPA-style)
        //     - Stage 2 (multi-EMA alignment)
        //     - 25% from 52w high (leader)
        //     - require_bos (structure)
        //     - HOLD (no whipsaw)
        //     - Wide trail (let winners run)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "HQ", base,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "require_bos", true,
                "ha_flip_exit", false,
                "trail_atr_mult", 1.5,
                "trail_activation_pct", 5.0);

        smcl(s, "R32", "HQ_TR1", base,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "require_bos", true,
                "ha_flip_exit", false,
                "trail_atr_mult", 1.0,
                "trail_activation_pct", 3.0);

        smcl(s, "R32", "HQ_TR2", base,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "require_bos", true,
                "ha_flip_exit", false,
                "trail_atr_mult", 2.0,
                "trail_activation_pct", 5.0);

        smcl(s, "R32", "HQ_SL2", base,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "require_bos", true,
                "sl_atr_mult", 2.0,
                "trail_atr_mult", 1.5,
                "trail_activation_pct", 5.0);

        // ─────────────────────────────────────────────────────────
        //  H. ALTERNATE HIGH QUALITY (looser proximity, with structure)
        // ─────────────────────────────────────────────────────────
        smcl(s, "R32", "HQ_LOOSE", base,
                "stage2_filter", true,
                "max_pct_from_high", 35.0,
                "ha_flip_exit", false,
                "trail_atr_mult", 1.5,
                "trail_activation_pct", 5.0);

        smcl(s, "R32", "T200_H25_HOLD", base,
                "trend_ema", 200,
                "max_pct_from_high", 25.0,
                "ha_flip_exit", false,
                "trail_atr_mult", 1.5,
                "trail_activation_pct", 5.0);
    }

    // ══════════════════════════════════════════════════════════════
    //  R33 — Triple-Crown: Strategies that dominate ALL 3 pillars
    //
    //  Observation: Score 99+ requires top-percentile on Sharpe AND
    //  Calmar AND Robustness simultaneously. Strategies that win one
    //  pillar but lose another can't hit 99+.
    //
    //  Triple-crown pattern (proven on Power, FMCG, Healthcare):
    //    1. Strict trend filter (ADX 25+, EMA slope rising) → Robust
    //    2. HA flip entry → high frequency consistency → Sharpe
    //    3. Wide trail + no early exit → big winners → Calmar
    //
    //  R33 generates ~200 variants combining these three principles
    //  with intensive parameter sweeps to find sector-specific winners.
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR33(Map<String, Supplier<Strategy>> s) {

        // ═══════════════════════════════════════════════════════
        //  A. HA + ADX filter (Power sector found 99+ this way)
        //     Triple-crown candidates: strict trend + HA + wide trail
        // ═══════════════════════════════════════════════════════
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("tp_atr_mult", 0.0);
        haBase.put("atr_period", 14);

        for (double adx : new double[]{20, 25, 30, 35}) {
            for (double tr : new double[]{0.4, 0.5, 0.7, 1.0}) {
                for (double a : new double[]{2.0, 3.0, 4.0}) {
                    ha(s, "R33", String.format("ADX%.0f_TR%.1f_A%.0f", adx, tr, a), haBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. HA + EMA50 slope rising (IT sector loved this)
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{50, 100, 200}) {
            for (int slope : new int[]{3, 5, 10, 20}) {
                for (double tr : new double[]{0.5, 1.0, 1.5, 2.0}) {
                    ha(s, "R33", String.format("E%d_SLP%d_TR%.1f", ema, slope, tr), haBase,
                            "trend_ema", ema, "ema_slope_bars", slope,
                            "trail_atr_mult", tr, "trail_activation_pct", 3.0);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. HA + TONLY (no HA flip exit) + ADX filter (FMCG/Healthcare style)
        //     Triple-crown: trail-only + strict filter = clean Sharpe + huge winners
        // ═══════════════════════════════════════════════════════
        Map<String, Object> tonlyBase = new HashMap<>(haBase);
        tonlyBase.put("ha_flip_exit", false);

        for (double adx : new double[]{20, 25, 30}) {
            for (double tr : new double[]{3.0, 5.0, 7.0}) {
                for (double a : new double[]{3.0, 5.0, 7.0}) {
                    ha(s, "R33", String.format("TONLY_ADX%.0f_TR%.0f_A%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. HA + EMA + TONLY (combo: trend + no early exit)
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{50, 100, 200}) {
            for (double tr : new double[]{3.0, 5.0, 7.0}) {
                for (double a : new double[]{2.0, 3.0, 5.0}) {
                    ha(s, "R33", String.format("TONLY_E%d_TR%.0f_A%.0f", ema, tr, a), tonlyBase,
                            "trend_ema", ema,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. HA flip + activation_pct sweep (Capital Goods loved A15+)
        //     Wider activation = let early gains breathe
        // ═══════════════════════════════════════════════════════
        for (double a : new double[]{6.0, 8.0, 10.0, 12.0, 15.0, 18.0, 20.0, 25.0}) {
            for (double tr : new double[]{0.3, 0.5, 0.7, 1.0}) {
                ha(s, "R33", String.format("WIDE_A%.0f_TR%.1f", a, tr), haBase,
                        "trail_activation_pct", a, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. SMC Lite swing-period sweep (Financial sector loved SW40)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> smclBase = new HashMap<>();
        smclBase.put("require_bos", false);
        smclBase.put("trend_ema", 0);
        smclBase.put("entry_mode", "ha_flip");
        smclBase.put("bos_entry", false);
        smclBase.put("ha_flip_exit", true);
        smclBase.put("sl_atr_mult", 0.0);
        smclBase.put("trailing_stop", true);
        smclBase.put("trail_atr_mult", 0.5);
        smclBase.put("trail_activation_pct", 3.0);
        smclBase.put("bos_exit", true);
        smclBase.put("swing_right", 3);
        smclBase.put("atr_period", 14);

        for (int sw : new int[]{25, 30, 35, 40, 45, 50, 60, 80}) {
            smcl(s, "R33", String.format("SW%d", sw), smclBase, "swing_left", sw);
        }

        // ═══════════════════════════════════════════════════════
        //  G. PB (pullback) entry — Auto sector winner
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbBase = new HashMap<>(smclBase);
        pbBase.put("entry_mode", "pullback");
        pbBase.put("swing_left", 20);

        for (int eema : new int[]{5, 10, 15, 20}) {
            for (double tr : new double[]{0.3, 0.5, 0.7, 1.0}) {
                for (double a : new double[]{2.0, 3.0, 5.0}) {
                    smcl(s, "R33", String.format("PB_E%d_TR%.1f_A%.0f", eema, tr, a), pbBase,
                            "entry_ema", eema, "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. Multi-filter combo: EMA + ADX + slope (the holy grail)
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{50, 100}) {
            for (double adx : new double[]{20, 25}) {
                for (double tr : new double[]{0.5, 1.0, 1.5}) {
                    ha(s, "R33", String.format("E%d_ADX%.0f_TR%.1f", ema, adx, tr), haBase,
                            "trend_ema", ema,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  I. TP target variants (Consumer Durables loved TP5)
        // ═══════════════════════════════════════════════════════
        for (double tpAtr : new double[]{3.0, 4.0, 5.0, 6.0, 7.0}) {
            for (double a : new double[]{1.0, 2.0, 3.0}) {
                ha(s, "R33", String.format("TP%.0f_A%.0f", tpAtr, a), haBase,
                        "tp_atr_mult", tpAtr, "trail_activation_pct", a);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R34 — Fine sweeps around winning patterns to hit 99+
    //  Focus: 5 sectors stuck at 98-99 due to Sharpe pillar gap.
    //  Strategy: smaller parameter steps + Sharpe-boosting variants
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR34(Map<String, Supplier<Strategy>> s) {

        // Common HA base
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("atr_period", 14);

        Map<String, Object> tonlyBase = new HashMap<>(haBase);
        tonlyBase.put("ha_flip_exit", false);

        // ═══════════════════════════════════════════════════════
        //  A. Capital Goods refinement: WIDE_A15_TR0.5 sweep
        //     Current top: A15, TR0.5 → fine sweep around it
        // ═══════════════════════════════════════════════════════
        for (double a : new double[]{13.0, 14.0, 15.0, 16.0, 17.0}) {
            for (double tr : new double[]{0.4, 0.5, 0.6}) {
                ha(s, "R34", String.format("WIDE_A%.0f_TR%.1f", a, tr), haBase,
                        "trail_activation_pct", a, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. IT refinement: EMA50_SLP sweep with HA flip
        // ═══════════════════════════════════════════════════════
        for (int slope : new int[]{3, 4, 5, 6, 7, 8, 10}) {
            for (double tr : new double[]{0.5, 0.7, 1.0, 1.5}) {
                for (double a : new double[]{2.0, 3.0, 4.0}) {
                    ha(s, "R34", String.format("E50_SLP%d_TR%.1f_A%.0f", slope, tr, a), haBase,
                            "trend_ema", 50, "ema_slope_bars", slope,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. Consumer Durables: TP variants with smaller steps
        // ═══════════════════════════════════════════════════════
        for (double tp : new double[]{3.5, 4.0, 4.5, 5.0, 5.5}) {
            for (double a : new double[]{1.0, 1.5, 2.0, 2.5, 3.0}) {
                ha(s, "R34", String.format("TP%.1f_A%.1f", tp, a), haBase,
                        "tp_atr_mult", tp, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. Automobile: PB pullback variants (better Robust)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbBase = new HashMap<>();
        pbBase.put("require_bos", false);
        pbBase.put("entry_mode", "pullback");
        pbBase.put("bos_exit", true);
        pbBase.put("swing_left", 20);
        pbBase.put("swing_right", 3);
        pbBase.put("trailing_stop", true);
        pbBase.put("ha_flip_exit", true);
        pbBase.put("atr_period", 14);

        for (int eema : new int[]{8, 10, 12, 15}) {
            for (double tr : new double[]{0.4, 0.5, 0.6, 0.8}) {
                for (double a : new double[]{2.0, 3.0, 4.0, 5.0}) {
                    smcl(s, "R34", String.format("PB%d_TR%.1f_A%.0f", eema, tr, a), pbBase,
                            "entry_ema", eema, "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. Healthcare: TONLY ADX30 fine sweep
        //     Current top scored 98.97 — push for 99+
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{27, 28, 29, 30, 31, 32, 33}) {
            for (double tr : new double[]{4.0, 4.5, 5.0, 5.5, 6.0}) {
                for (double a : new double[]{5.0, 6.0, 7.0, 8.0}) {
                    ha(s, "R34", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R35 — Robustness-focused: target ROBUST pillar bottleneck
    //
    //  Diagnosis: 6 of 9 stuck sectors have Robust pillar < 99
    //  Especially Auto (78.2) — strategy works on some stocks, fails on others
    //
    //  Approach: Ultra-selective filters that only trade in the IDEAL
    //  conditions, ensuring cross-asset consistency:
    //   - Stage 2 (multi-EMA alignment)
    //   - 52-week high proximity (only leaders)
    //   - High ADX (only strong trends)
    //   - Volatility cap (skip chop)
    //   - Combination filters
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR35(Map<String, Supplier<Strategy>> s) {

        // SMCL base (has full filter support)
        Map<String, Object> smclBase = new HashMap<>();
        smclBase.put("require_bos", false);
        smclBase.put("entry_mode", "ha_flip");
        smclBase.put("bos_entry", false);
        smclBase.put("ha_flip_exit", true);
        smclBase.put("trailing_stop", true);
        smclBase.put("trail_atr_mult", 0.5);
        smclBase.put("trail_activation_pct", 3.0);
        smclBase.put("bos_exit", true);
        smclBase.put("swing_left", 20);
        smclBase.put("swing_right", 3);
        smclBase.put("atr_period", 14);
        smclBase.put("sl_atr_mult", 0.0);

        // ═══════════════════════════════════════════════════════
        //  A. STAGE 2 ONLY — Minervini multi-EMA alignment
        //     Only enters when close > EMA50 > EMA150 > EMA200
        //     Maximally selective → high Robust
        // ═══════════════════════════════════════════════════════
        for (double tr : new double[]{0.5, 1.0, 2.0, 3.0}) {
            for (double a : new double[]{2.0, 3.0, 5.0, 10.0}) {
                smcl(s, "R35", String.format("STAGE2_TR%.1f_A%.0f", tr, a), smclBase,
                        "stage2_filter", true,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. STAGE 2 + 52w high proximity (top leaders only)
        // ═══════════════════════════════════════════════════════
        for (double h : new double[]{15.0, 25.0, 35.0}) {
            for (double tr : new double[]{0.5, 1.0, 2.0}) {
                smcl(s, "R35", String.format("STAGE2_H%.0f_TR%.1f", h, tr), smclBase,
                        "stage2_filter", true,
                        "max_pct_from_high", h,
                        "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. STAGE 2 + EMA slope rising + ADX
        //     Triple-confirmed Stage 2 = ultra-robust
        // ═══════════════════════════════════════════════════════
        for (int slope : new int[]{5, 10, 20}) {
            for (double adx : new double[]{20, 25, 30}) {
                smcl(s, "R35", String.format("S2_SLP%d_ADX%.0f", slope, adx), smclBase,
                        "stage2_filter", true,
                        "trend_ema", 200, "ema_slope_bars", slope,
                        "adx_filter", true, "adx_threshold", adx);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. HIGH ADX 35/40 (extreme trend selectivity)
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{35, 40, 45}) {
            for (double tr : new double[]{0.5, 1.0, 2.0, 3.0}) {
                for (double a : new double[]{3.0, 5.0}) {
                    smcl(s, "R35", String.format("ADX%.0f_TR%.1f_A%.0f", adx, tr, a), smclBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. VOLATILITY CAP (skip choppy stocks)
        // ═══════════════════════════════════════════════════════
        for (double atrPct : new double[]{2.0, 3.0, 4.0, 5.0}) {
            for (double tr : new double[]{0.5, 1.0}) {
                smcl(s, "R35", String.format("VOL%.0f_TR%.1f", atrPct, tr), smclBase,
                        "atr_pct_max", atrPct, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. THE ULTIMATE: Stage 2 + ADX + slope + vol cap
        //     This is essentially Minervini SEPA in code
        // ═══════════════════════════════════════════════════════
        smcl(s, "R35", "SEPA_FULL", smclBase,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "trend_ema", 200, "ema_slope_bars", 20,
                "adx_filter", true, "adx_threshold", 20.0,
                "atr_pct_max", 5.0,
                "rsi_floor", 50.0,
                "trail_atr_mult", 1.5, "trail_activation_pct", 5.0);

        smcl(s, "R35", "SEPA_FULL_HOLD", smclBase,
                "stage2_filter", true,
                "max_pct_from_high", 25.0,
                "trend_ema", 200, "ema_slope_bars", 20,
                "adx_filter", true, "adx_threshold", 20.0,
                "atr_pct_max", 5.0,
                "rsi_floor", 50.0,
                "ha_flip_exit", false,
                "trail_atr_mult", 2.0, "trail_activation_pct", 5.0);

        smcl(s, "R35", "SEPA_TIGHT", smclBase,
                "stage2_filter", true,
                "max_pct_from_high", 15.0,
                "adx_filter", true, "adx_threshold", 25.0,
                "trail_atr_mult", 1.0, "trail_activation_pct", 3.0);

        // ═══════════════════════════════════════════════════════
        //  G. AUTO SECTOR SPECIFIC (Robust 78 → needs major boost)
        //     Auto stocks are very heterogeneous — strict filters needed
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{15, 18, 20, 22, 25}) {
            for (double h : new double[]{20.0, 30.0, 40.0}) {
                smcl(s, "R35", String.format("AUTO_ADX%.0f_H%.0f", adx, h), smclBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "max_pct_from_high", h);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. PULLBACK + Stage 2 (Auto loves PB, add filters)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbBase = new HashMap<>(smclBase);
        pbBase.put("entry_mode", "pullback");
        for (int eema : new int[]{8, 10, 12}) {
            smcl(s, "R35", String.format("PB%d_STAGE2", eema), pbBase,
                    "entry_ema", eema, "stage2_filter", true);
            smcl(s, "R35", String.format("PB%d_ADX25", eema), pbBase,
                    "entry_ema", eema, "adx_filter", true, "adx_threshold", 25.0);
            smcl(s, "R35", String.format("PB%d_H25", eema), pbBase,
                    "entry_ema", eema, "max_pct_from_high", 25.0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R36 — Cross-pollinate winning patterns across sectors
    //
    //  Realty winner: MOM126_E20 (pure 6-mo momentum) → PF 10, MDD -25%
    //  Apply MOM pattern to other sectors with multi-lookback sweep.
    //
    //  Plus: Multi-bar HA confirmation for Auto (Robust 78 → boost)
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR36(Map<String, Supplier<Strategy>> s) {

        // ═══════════════════════════════════════════════════════
        //  A. MOMENTUM (dual_mom) — Realty's winning pattern
        //     Fine-grained lookback x ema_smoothing sweep
        // ═══════════════════════════════════════════════════════
        Map<String, Object> momBase = new HashMap<>();
        momBase.put("strategy_type", "dual_mom");
        momBase.put("atr_period", 14);
        momBase.put("sl_atr_mult", 0.0);
        momBase.put("tp_atr_mult", 0.0);

        for (int lb : new int[]{63, 90, 126, 180, 200, 252}) {
            for (int ema : new int[]{5, 10, 20, 30, 50}) {
                quant(s, "R36", String.format("MOM%d_E%d", lb, ema), momBase,
                        "mom_lookback", lb, "mom_ema", ema);
            }
        }

        // Momentum + trend filter (avoid downtrend false signals)
        for (int lb : new int[]{90, 126, 180}) {
            for (int trend : new int[]{50, 100, 200}) {
                quant(s, "R36", String.format("MOM%d_T%d", lb, trend), momBase,
                        "mom_lookback", lb, "mom_ema", 20, "trend_ema", trend);
            }
        }

        // Momentum + ATR stop (cap downside)
        for (int lb : new int[]{126, 180, 252}) {
            for (double sl : new double[]{2.0, 3.0, 4.0}) {
                quant(s, "R36", String.format("MOM%d_SL%.0f", lb, sl), momBase,
                        "mom_lookback", lb, "mom_ema", 20, "sl_atr_mult", sl);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. MOMENTUM + EMA SMOOTHED (intermediate horizon)
        //     Best for sectors with seasonal cycles
        // ═══════════════════════════════════════════════════════
        for (int lb : new int[]{42, 63, 90}) {
            for (int ema : new int[]{15, 20, 25}) {
                quant(s, "R36", String.format("MOMSHORT%d_E%d", lb, ema), momBase,
                        "mom_lookback", lb, "mom_ema", ema);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. MULTI-BAR HA CONFIRMATION (Auto sector booster)
        //     Wait for N consecutive bullish HA bars before entry
        //     Uses haExitBars for entry confirmation indirectly
        // ═══════════════════════════════════════════════════════
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("sl_atr_mult", 0.0);
        haBase.put("atr_period", 14);

        // HA + strong candle (no lower wick = strong buying)
        for (double tr : new double[]{0.5, 1.0, 1.5}) {
            for (double a : new double[]{2.0, 3.0, 5.0}) {
                ha(s, "R36", String.format("STRONG_TR%.1f_A%.0f", tr, a), haBase,
                        "strong_candle", true,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // HA + min_trend_bars (require trend in place before entry)
        for (int mt : new int[]{3, 5, 7, 10}) {
            for (double tr : new double[]{0.5, 1.0, 2.0}) {
                ha(s, "R36", String.format("MT%d_TR%.1f", mt, tr), haBase,
                        "min_trend_bars", mt, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. CHANDELIER + Stage 2 (sustained trend exit)
        //     Chandelier exit + Stage 2 filter = max robustness
        // ═══════════════════════════════════════════════════════
        Map<String, Object> chBase = new HashMap<>();
        chBase.put("strategy_type", "chandelier");
        chBase.put("atr_period", 14);

        for (int p : new int[]{14, 22, 30}) {
            for (double m : new double[]{2.0, 3.0, 4.0, 5.0}) {
                quant(s, "R36", String.format("CH%d_M%.0f_T200", p, m), chBase,
                        "chandelier_period", p, "chandelier_mult", m,
                        "trend_ema", 200);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. HYBRID: HA + EMA50 + RSI + wide trail
        //     Combines IT winner ingredients
        // ═══════════════════════════════════════════════════════
        Map<String, Object> smclHybrid = new HashMap<>();
        smclHybrid.put("require_bos", false);
        smclHybrid.put("entry_mode", "ha_flip");
        smclHybrid.put("bos_entry", false);
        smclHybrid.put("ha_flip_exit", true);
        smclHybrid.put("trailing_stop", true);
        smclHybrid.put("bos_exit", true);
        smclHybrid.put("swing_left", 20);
        smclHybrid.put("swing_right", 3);
        smclHybrid.put("atr_period", 14);
        smclHybrid.put("sl_atr_mult", 0.0);

        for (int ema : new int[]{50, 100}) {
            for (int slope : new int[]{5, 10, 20}) {
                for (double tr : new double[]{1.0, 2.0, 3.0}) {
                    smcl(s, "R36", String.format("HYBRID_E%d_SLP%d_TR%.0f", ema, slope, tr), smclHybrid,
                            "trend_ema", ema, "ema_slope_bars", slope,
                            "trail_atr_mult", tr, "trail_activation_pct", 3.0);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. VOLUME-CONFIRMED HA (Construction's winning pattern applied broadly)
        // ═══════════════════════════════════════════════════════
        for (double vm : new double[]{1.5, 2.0, 2.5}) {
            for (double tr : new double[]{0.5, 1.0, 2.0}) {
                ha(s, "R36", String.format("VOL%.1f_TR%.1f", vm, tr), haBase,
                        "volume_filter", true, "vol_mult", vm,
                        "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. VOLUME + ADX + EMA combo (the safest)
        // ═══════════════════════════════════════════════════════
        for (double vm : new double[]{1.5, 2.0}) {
            for (double adx : new double[]{20, 25, 30}) {
                ha(s, "R36", String.format("VOL%.1f_ADX%.0f", vm, adx), haBase,
                        "volume_filter", true, "vol_mult", vm,
                        "adx_filter", true, "adx_threshold", adx);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R37 — MDD-REDUCTION overlay on existing sector winners
    //
    //  Goal: Lower MDD for each sector WITHOUT losing CAGR or score
    //
    //  Approach:
    //   - Add ATR-based hard stop loss (caps single-trade loss)
    //   - Add percentage max loss (alternative cap)
    //   - Add breakeven trigger (move SL to entry after X% profit)
    //   - Apply to each sector's winning pattern
    //
    //  Theory: Single-trade losses contribute most to portfolio MDD.
    //  Capping them via hard stop should reduce MDD with minimal CAGR loss.
    // ══════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════
    //  R40 — THIRD-PASS: UNTESTED COMBO PAIRS (~600 new variants)
    //
    //  Gap analysis from R39 top10:
    //  1. WIDE + TP (never combined) — biggest gap
    //  2. RSI + WIDE + TP (triple combo)
    //  3. PB + CD + TP (triple combo)
    //  4. CD + WIDE (never combined)
    //  5. Regime + TP (never combined)
    //  6. W35/W40 (wider than W30 never tested)
    //  7. Fine-tune each sector winner ±1 ADX, ±0.5 trail, ±5 TP
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR40(Map<String, Supplier<Strategy>> s) {

        // ── Common bases ─────────────────────────────────────────
        Map<String, Object> tonlyBase = new HashMap<>();
        tonlyBase.put("entry_mode", "flip");
        tonlyBase.put("ha_flip_exit", false);
        tonlyBase.put("trailing_stop", true);
        tonlyBase.put("atr_period", 14);

        Map<String, Object> pbTonly = new HashMap<>();
        pbTonly.put("entry_mode", "pullback");
        pbTonly.put("min_trend_bars", 5);
        pbTonly.put("max_pullback_bars", 2);
        pbTonly.put("ha_flip_exit", false);
        pbTonly.put("trailing_stop", true);
        pbTonly.put("atr_period", 14);

        Map<String, Object> rsiPbBase = new HashMap<>();
        rsiPbBase.put("entry_mode", "rsi_pullback");
        rsiPbBase.put("rsi_period", 2);
        rsiPbBase.put("ha_flip_exit", false);
        rsiPbBase.put("trailing_stop", true);
        rsiPbBase.put("atr_period", 14);

        Map<String, Object> regimeBase = new HashMap<>();
        regimeBase.put("entry_mode", "regime");
        regimeBase.put("ha_flip_exit", false);
        regimeBase.put("trailing_stop", true);
        regimeBase.put("atr_period", 14);

        // ═══════════════════════════════════════════════════════
        //  A. TONLY + WIDE + TP (never combined!)
        //     Healthcare winner=ADX27_TR3_W30, add TP to lock gains
        //     Financial winner=ADX27_TR3_W30, add TP
        //     Target: ALL sectors — this is the biggest untested combo
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 27, 30, 33}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double w : new double[]{15, 20, 25, 30, 35}) {
                    for (double tp : new double[]{10, 15, 20, 30}) {
                        ha(s, "R40", String.format("TONLY_ADX%.0f_TR%.0f_W%.0f_TP%.0f", adx, tr, w, tp), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", w,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. RSI + WIDE + TP (triple combo — never combined!)
        //     FMCG winner=RSI45_ADX30_TR3_W20, add TP
        //     OilGas winner=RSI60_ADX30_TR3_W20, add TP
        //     ConsumerServices winner=RSI45_ADX30_TR4_TP10, add WIDE
        //     Target: FMCG, oil_gas, consumer_services, textiles
        // ═══════════════════════════════════════════════════════
        for (double rsi : new double[]{40, 45, 50, 55, 60}) {
            for (double adx : new double[]{25, 30}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double w : new double[]{10, 15, 20, 25}) {
                        for (double tp : new double[]{10, 15, 20}) {
                            ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR%.0f_W%.0f_TP%.0f", rsi, adx, tr, w, tp), rsiPbBase,
                                    "rsi_entry", rsi,
                                    "adx_filter", true, "adx_threshold", adx,
                                    "trail_atr_mult", tr, "trail_activation_pct", w,
                                    "tp_atr_mult", tp);
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. PB + CD + TP (triple combo — PB+TP tested, PB+CD tested,
        //     but PB+CD+TP never combined!)
        //     Capital_goods winner=PB_ADX15_TR5_A5_TP30
        //     Metals winner=PB_ADX15_CD50_TR3_A7
        //     Combine best of both
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{13, 15, 17, 20}) {
            for (int cd : new int[]{20, 30, 50}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        for (double tp : new double[]{10, 20, 30}) {
                            ha(s, "R40", String.format("PB_ADX%.0f_CD%d_TR%.0f_A%.0f_TP%.0f", adx, cd, tr, a, tp), pbTonly,
                                    "adx_filter", true, "adx_threshold", adx,
                                    "cooldown_bars", cd,
                                    "trail_atr_mult", tr, "trail_activation_pct", a,
                                    "tp_atr_mult", tp);
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. CD + WIDE (never combined!)
        //     IT winner=ADX30_CD30_TR5_A10 → try CD + wider activation
        //     Services=RSI45_ADX30_TR5_TP10 → try CD+WIDE on TONLY
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{27, 30, 33}) {
            for (int cd : new int[]{20, 30, 40}) {
                for (double tr : new double[]{4.0, 5.0}) {
                    for (double w : new double[]{15, 20, 25, 30}) {
                        ha(s, "R40", String.format("TONLY_ADX%.0f_CD%d_TR%.0f_W%.0f", adx, cd, tr, w), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "cooldown_bars", cd,
                                "trail_atr_mult", tr, "trail_activation_pct", w);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. REGIME + TP (never combined!)
        //     Construction winner=REG_W15_T80_CD30_TR5
        //     Realty winner=REG_W12_T85_R50_TR5
        //     Add TP to lock gains on regime entries
        // ═══════════════════════════════════════════════════════
        for (int window : new int[]{12, 15, 20}) {
            for (double threshold : new double[]{0.75, 0.80, 0.85}) {
                for (double rsiEntry : new double[]{40, 50}) {
                    for (double tr : new double[]{4.0, 5.0, 6.0}) {
                        for (double tp : new double[]{10, 15, 20, 30}) {
                            ha(s, "R40", String.format("REG_W%d_T%.0f_R%.0f_TR%.0f_TP%.0f", window, threshold * 100, rsiEntry, tr, tp), regimeBase,
                                    "regime_window", window, "regime_threshold", threshold,
                                    "rsi_entry", rsiEntry,
                                    "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                                    "tp_atr_mult", tp);
                        }
                    }
                }
            }
        }
        // Regime + CD + TP (construction/realty triple combo)
        for (int window : new int[]{12, 15}) {
            for (double threshold : new double[]{0.80, 0.85}) {
                for (int cd : new int[]{20, 30}) {
                    for (double tr : new double[]{4.0, 5.0}) {
                        for (double tp : new double[]{10, 15, 20}) {
                            ha(s, "R40", String.format("REG_W%d_T%.0f_CD%d_TR%.0f_TP%.0f", window, threshold * 100, cd, tr, tp), regimeBase,
                                    "regime_window", window, "regime_threshold", threshold,
                                    "rsi_entry", 40.0,
                                    "cooldown_bars", cd,
                                    "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                                    "tp_atr_mult", tp);
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. W35/W40 (wider than W30 never tested)
        //     Healthcare/financial topped at W30. Maybe W35-40 is better.
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 26, 27, 28}) {
            for (double tr : new double[]{2.5, 3.0, 3.5}) {
                for (double w : new double[]{35, 40}) {
                    ha(s, "R40", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. FINE-TUNE EACH R39 WINNER (±1 ADX, ±0.5 trail, ±5 TP/W)
        // ═══════════════════════════════════════════════════════

        // capital_goods: PB_ADX15_TR5_A5_TP30 → ADX 14-16, TR 4.5-5.5, TP 25-35
        for (double adx : new double[]{14, 16}) {
            for (double tr : new double[]{4.5, 5.5}) {
                for (double tp : new double[]{25, 35}) {
                    ha(s, "R40", String.format("PB_ADX%.0f_TR%.1f_A5_TP%.0f", adx, tr, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // consumer_services: RSI45_ADX30_TR4_TP10 → RSI 40-50, ADX 28-32, TP 8-12
        for (double rsi : new double[]{40, 42, 48, 50}) {
            for (double adx : new double[]{28, 29, 31, 32}) {
                for (double tp : new double[]{8, 12}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR4_TP%.0f", rsi, adx, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // metals: PB_ADX15_CD50_TR3_A7 → CD 40-60, ADX 13-17
        for (double adx : new double[]{13, 14, 16, 17}) {
            for (int cd : new int[]{40, 45, 55, 60}) {
                ha(s, "R40", String.format("PB_ADX%.0f_CD%d_TR3_A7", adx, cd), pbTonly,
                        "adx_filter", true, "adx_threshold", adx,
                        "cooldown_bars", cd,
                        "trail_atr_mult", 3.0, "trail_activation_pct", 7.0);
            }
        }

        // IT: TONLY_ADX30_CD30_TR5_A10 → ADX 28-32, CD 25-35
        for (double adx : new double[]{28, 29, 31, 32}) {
            for (int cd : new int[]{25, 35}) {
                for (double a : new double[]{8, 10, 12}) {
                    ha(s, "R40", String.format("TONLY_ADX%.0f_CD%d_TR5_A%.0f", adx, cd, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 5.0, "trail_activation_pct", a);
                }
            }
        }

        // power: PB_ADX20_TR3_A5_TP10 → ADX 18-22, TP 8-12
        for (double adx : new double[]{18, 19, 21, 22}) {
            for (double tp : new double[]{8, 12, 15}) {
                ha(s, "R40", String.format("PB_ADX%.0f_TR3_A5_TP%.0f", adx, tp), pbTonly,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 3.0, "trail_activation_pct", 5.0,
                        "tp_atr_mult", tp);
            }
        }

        // textiles: RSI50_ADX25_TR4_TP15 → RSI 48-52, ADX 23-27, TP 12-18
        for (double rsi : new double[]{48, 52}) {
            for (double adx : new double[]{23, 24, 26, 27}) {
                for (double tp : new double[]{12, 13, 14, 17, 18}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR4_TP%.0f", rsi, adx, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // services: RSI45_ADX30_TR5_TP10 → RSI 42-48, TP 8-12, + CD combo
        for (double rsi : new double[]{42, 43, 47, 48}) {
            for (double tp : new double[]{8, 12}) {
                ha(s, "R40", String.format("RSI%.0f_ADX30_TR5_TP%.0f", rsi, tp), rsiPbBase,
                        "rsi_entry", rsi,
                        "adx_filter", true, "adx_threshold", 30.0,
                        "trail_atr_mult", 5.0, "trail_activation_pct", 5.0,
                        "tp_atr_mult", tp);
            }
        }
        // services: RSI + CD + TP combo
        for (double rsi : new double[]{45, 50}) {
            for (int cd : new int[]{10, 20, 30}) {
                for (double tp : new double[]{10, 15}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX30_CD%d_TR5_TP%.0f", rsi, cd, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", 30.0,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 5.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. CHEMICALS/CONSTRUCTION_MAT/AUTO — stubborn sectors
        //     Try WIDE+TP, RSI+WIDE, PB+TP patterns on them
        // ═══════════════════════════════════════════════════════
        // chemicals: winner ADX31_TR4_A5 — try RSI+WIDE, PB+TP
        for (double rsi : new double[]{45, 50, 55}) {
            for (double adx : new double[]{28, 30, 31}) {
                for (double w : new double[]{15, 20, 25}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR4_W%.0f", rsi, adx, w), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", w);
                }
            }
        }
        // chemicals: TONLY + CD (never tried on chemicals)
        for (double adx : new double[]{29, 30, 31, 32}) {
            for (int cd : new int[]{20, 30, 40}) {
                ha(s, "R40", String.format("TONLY_ADX%.0f_CD%d_TR4_A5", adx, cd), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "cooldown_bars", cd,
                        "trail_atr_mult", 4.0, "trail_activation_pct", 5.0);
            }
        }

        // automobile: TONLY_ADX30_TR5_A5 → try + WIDE, + TP, + CD combos
        for (double adx : new double[]{28, 29, 30, 31}) {
            for (double w : new double[]{15, 20, 25}) {
                ha(s, "R40", String.format("TONLY_ADX%.0f_TR5_W%.0f", adx, w), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 5.0, "trail_activation_pct", w);
            }
        }
        for (double adx : new double[]{28, 30, 31}) {
            for (double tp : new double[]{15, 20, 30}) {
                ha(s, "R40", String.format("TONLY_ADX%.0f_TR5_A5_TP%.0f", adx, tp), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 5.0, "trail_activation_pct", 5.0,
                        "tp_atr_mult", tp);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  I. TELECOM SPECIAL — only PB12_TONLY_ADX20_TR3_A7 works
        //     Try PB + TP, PB + WIDE, PB + CD, RSI + TP
        // ═══════════════════════════════════════════════════════
        Map<String, Object> smclPbTonly = new HashMap<>();
        smclPbTonly.put("require_bos", false);
        smclPbTonly.put("entry_mode", "pullback");
        smclPbTonly.put("bos_entry", false);
        smclPbTonly.put("ha_flip_exit", false);
        smclPbTonly.put("trailing_stop", true);
        smclPbTonly.put("sl_atr_mult", 0.0);

        for (int ema : new int[]{10, 12, 14}) {
            for (double adx : new double[]{18, 20, 22}) {
                for (double tp : new double[]{10, 15, 20}) {
                    smcl(s, "R40", String.format("PB%d_ADX%.0f_TR3_A7_TP%.0f", ema, adx, tp), smclPbTonly,
                            "entry_ema", ema,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 3.0, "trail_activation_pct", 7.0,
                            "tp_atr_mult", tp);
                }
            }
        }
        for (int ema : new int[]{10, 12, 14}) {
            for (double adx : new double[]{18, 20, 22}) {
                for (int cd : new int[]{10, 20, 30}) {
                    smcl(s, "R40", String.format("PB%d_ADX%.0f_CD%d_TR3_A7", ema, adx, cd), smclPbTonly,
                            "entry_ema", ema,
                            "adx_filter", true, "adx_threshold", adx,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 3.0, "trail_activation_pct", 7.0);
                }
            }
        }
        // Telecom RSI + TP
        for (double rsi : new double[]{45, 50, 55}) {
            for (double adx : new double[]{20, 25}) {
                for (double tp : new double[]{10, 15, 20}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR3_A5_TP%.0f", rsi, adx, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 3.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  J. OILGAS fine-tune: RSI60_ADX30_TR3_W20
        //     Try RSI 55-65, W 18-25, + TP combo
        // ═══════════════════════════════════════════════════════
        for (double rsi : new double[]{55, 58, 62, 65}) {
            for (double adx : new double[]{28, 29, 30, 31}) {
                for (double w : new double[]{18, 22, 25}) {
                    ha(s, "R40", String.format("RSI%.0f_ADX%.0f_TR3_W%.0f", rsi, adx, w), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 3.0, "trail_activation_pct", w);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  K. CONSUMER_DURABLES fine-tune: TONLY_ADX33_TR5_A8_TP20
        //     Try ADX 32-34, TP 18-22, + WIDE combo
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{32, 34}) {
            for (double tr : new double[]{4.5, 5.0, 5.5}) {
                for (double a : new double[]{7, 8, 9}) {
                    for (double tp : new double[]{18, 22, 25}) {
                        ha(s, "R40", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f_TP%.0f", adx, tr, a, tp), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }
        // consumer_durables: WIDE + TP combo
        for (double adx : new double[]{30, 33}) {
            for (double w : new double[]{15, 20, 25}) {
                for (double tp : new double[]{15, 20, 25}) {
                    ha(s, "R40", String.format("TONLY_ADX%.0f_TR5_W%.0f_TP%.0f", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 5.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R41 — FOURTH-PASS: BEAT EVERY DEPLOYED WINNER (~400 new variants)
    //
    //  Strategy:
    //  A. 7 OLD-kept sectors need fundamentally different approaches
    //  B. 11 R40-upgraded sectors: fine-tune ±1-2 per param
    //  C. New combos: PB+WIDE+TP triple, high trail (6-7), RSI+CD+WIDE
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR41(Map<String, Supplier<Strategy>> s) {

        Map<String, Object> tonlyBase = new HashMap<>();
        tonlyBase.put("entry_mode", "flip");
        tonlyBase.put("ha_flip_exit", false);
        tonlyBase.put("trailing_stop", true);
        tonlyBase.put("atr_period", 14);

        Map<String, Object> pbTonly = new HashMap<>();
        pbTonly.put("entry_mode", "pullback");
        pbTonly.put("min_trend_bars", 5);
        pbTonly.put("max_pullback_bars", 2);
        pbTonly.put("ha_flip_exit", false);
        pbTonly.put("trailing_stop", true);
        pbTonly.put("atr_period", 14);

        Map<String, Object> rsiPbBase = new HashMap<>();
        rsiPbBase.put("entry_mode", "rsi_pullback");
        rsiPbBase.put("rsi_period", 2);
        rsiPbBase.put("ha_flip_exit", false);
        rsiPbBase.put("trailing_stop", true);
        rsiPbBase.put("atr_period", 14);

        Map<String, Object> regimeBase = new HashMap<>();
        regimeBase.put("entry_mode", "regime");
        regimeBase.put("ha_flip_exit", false);
        regimeBase.put("trailing_stop", true);
        regimeBase.put("atr_period", 14);

        // ═══════════════════════════════════════════════════════
        //  A. CONSTRUCTION_MATERIALS — winner R34:TONLY_ADX33_TR4.5_A5
        //     Gap: never tried WIDE, TP, PB, RSI, CD on this sector
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{31, 32, 33, 34, 35}) {
            for (double tr : new double[]{4.0, 4.5, 5.0}) {
                for (double w : new double[]{10, 15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }
        for (double adx : new double[]{31, 33, 35}) {
            for (double tp : new double[]{10, 15, 20, 25}) {
                ha(s, "R41", String.format("TONLY_ADX%.0f_TR4.5_A5_TP%.0f", adx, tp), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 4.5, "trail_activation_pct", 5.0,
                        "tp_atr_mult", tp);
            }
        }
        for (double adx : new double[]{20, 25, 30}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double tp : new double[]{10, 20, 30}) {
                    ha(s, "R41", String.format("PB_ADX%.0f_TR%.0f_A5_TP%.0f", adx, tr, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. CONSUMER_DURABLES — winner R34:TONLY_ADX27_TR5.0_A6
        //     Gap: higher trail mult (6-7), WIDE+TP together, PB entry
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 26, 27, 28, 29}) {
            for (double tr : new double[]{5.0, 5.5, 6.0, 6.5, 7.0}) {
                for (double a : new double[]{5, 6, 7, 8}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }
        for (double adx : new double[]{25, 27, 29}) {
            for (double w : new double[]{10, 15, 20}) {
                for (double tp : new double[]{15, 20, 25, 30}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR5_W%.0f_TP%.0f_V2", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 5.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. INFORMATION_TECHNOLOGY — winner R34:TONLY_ADX28_TR5.0_A8
        //     Gap: WIDE, TP, PB entry, RSI entry, CD+WIDE
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{26, 27, 28, 29, 30}) {
            for (double tr : new double[]{4.5, 5.0, 5.5, 6.0}) {
                for (double w : new double[]{10, 15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f_IT", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }
        for (double adx : new double[]{26, 28, 30}) {
            for (double tp : new double[]{10, 15, 20, 25}) {
                ha(s, "R41", String.format("TONLY_ADX%.0f_TR5_A8_TP%.0f", adx, tp), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 5.0, "trail_activation_pct", 8.0,
                        "tp_atr_mult", tp);
            }
        }
        for (double rsi : new double[]{45, 50, 55}) {
            for (double adx : new double[]{25, 28, 30}) {
                for (double tp : new double[]{10, 15, 20}) {
                    ha(s, "R41", String.format("RSI%.0f_ADX%.0f_TR5_W15_TP%.0f", rsi, adx, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 5.0, "trail_activation_pct", 15.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. OIL_GAS — winner R34:TONLY_ADX27_TR5.5_A8
        //     Gap: WIDE, TP, higher trail (6-7), RSI+WIDE+TP triple
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 26, 27, 28, 29}) {
            for (double tr : new double[]{5.0, 5.5, 6.0, 6.5}) {
                for (double w : new double[]{10, 15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f_OG", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }
        for (double adx : new double[]{25, 27, 29}) {
            for (double tp : new double[]{10, 15, 20, 25}) {
                ha(s, "R41", String.format("TONLY_ADX%.0f_TR5.5_A8_TP%.0f", adx, tp), tonlyBase,
                        "adx_filter", true, "adx_threshold", adx,
                        "trail_atr_mult", 5.5, "trail_activation_pct", 8.0,
                        "tp_atr_mult", tp);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. REALTY — winner R21:RSI_HEB2_VOL8 (very old, non-standard)
        //     Try everything: TONLY, PB, RSI, WIDE+TP, regime
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{20, 25, 28, 30, 33}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double w : new double[]{15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.0f_W%.0f_RE", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }
        for (double adx : new double[]{20, 25, 30}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double tp : new double[]{10, 15, 20}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.0f_A5_TP%.0f_RE", adx, tr, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }
        for (double adx : new double[]{15, 20, 25}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double tp : new double[]{10, 20, 30}) {
                    ha(s, "R41", String.format("PB_ADX%.0f_TR%.0f_A5_TP%.0f_RE", adx, tr, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }
        for (double rsi : new double[]{40, 50, 60}) {
            for (double adx : new double[]{20, 25, 30}) {
                for (double tp : new double[]{10, 15, 20}) {
                    ha(s, "R41", String.format("RSI%.0f_ADX%.0f_TR4_W15_TP%.0f_RE", rsi, adx, tp), rsiPbBase,
                            "rsi_entry", rsi,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", 15.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. SERVICES — winner HA_R30:REG_TP3 (regime strategy)
        //     Try: TONLY+WIDE+TP, PB+TP, RSI+WIDE, regime+WIDE+TP
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 28, 30, 33}) {
            for (double tr : new double[]{4.0, 5.0}) {
                for (double w : new double[]{15, 20, 25}) {
                    for (double tp : new double[]{10, 15, 20}) {
                        ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.0f_W%.0f_TP%.0f_SV", adx, tr, w, tp), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", w,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }
        for (int window : new int[]{10, 12, 15}) {
            for (double threshold : new double[]{0.80, 0.85, 0.90}) {
                for (double tr : new double[]{4.0, 5.0}) {
                    for (double tp : new double[]{10, 15, 20}) {
                        ha(s, "R41", String.format("REG_W%d_T%.0f_TR%.0f_W15_TP%.0f", window, threshold * 100, tr, tp), regimeBase,
                                "regime_window", window, "regime_threshold", threshold,
                                "rsi_entry", 40.0,
                                "trail_atr_mult", tr, "trail_activation_pct", 15.0,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. TEXTILES — winner HA_R31:RSI20_SL15_TP10 (old HA variant)
        //     Try: TONLY+WIDE+TP, PB+TP, RSI+WIDE+TP
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{22, 25, 27, 30}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double w : new double[]{15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.0f_W%.0f_TX", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
            }
        }
        for (double rsi : new double[]{40, 45, 50, 55}) {
            for (double adx : new double[]{22, 25, 28}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double tp : new double[]{10, 15, 20}) {
                        ha(s, "R41", String.format("RSI%.0f_ADX%.0f_TR%.0f_W15_TP%.0f_TX", rsi, adx, tr, tp), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", 15.0,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. FINE-TUNE 11 R40-UPGRADED SECTORS (±1-2 on each param)
        //     Focus on the sectors that already improved — squeeze more
        // ═══════════════════════════════════════════════════════

        // auto: R40:TONLY_ADX29_TR5_W20 → ADX 28-30, TR 4.5-5.5, W 18-22
        for (double adx : new double[]{28, 30}) {
            for (double tr : new double[]{4.5, 5.5}) {
                for (double w : new double[]{18, 22, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f_AU", adx, tr, w), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", w);
                }
                // also + TP
                for (double tp : new double[]{15, 20, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.1f_W20_TP%.0f_AU", adx, tr, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 20.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // capital_goods: R40:PB_ADX13_CD20_TR5_A5_TP30 → ADX 11-15, CD 15-25, TP 25-35
        for (double adx : new double[]{11, 12, 14, 15}) {
            for (int cd : new int[]{15, 25}) {
                for (double tp : new double[]{25, 35}) {
                    ha(s, "R41", String.format("PB_ADX%.0f_CD%d_TR5_A5_TP%.0f_CG", adx, cd, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 5.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // chemicals: R40:TONLY_ADX27_TR4_W30_TP10 → ADX 26-28, W 28-32, TP 8-12
        for (double adx : new double[]{26, 28}) {
            for (double w : new double[]{28, 32, 35}) {
                for (double tp : new double[]{8, 12, 15}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR4_W%.0f_TP%.0f_CH", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }

        // construction: R40:RSI50_ADX25_TR3_W25_TP15 → RSI 48-52, ADX 23-27, W 22-28
        for (double rsi : new double[]{48, 52}) {
            for (double adx : new double[]{23, 24, 26, 27}) {
                for (double w : new double[]{22, 28}) {
                    for (double tp : new double[]{12, 15, 18}) {
                        ha(s, "R41", String.format("RSI%.0f_ADX%.0f_TR3_W%.0f_TP%.0f_CN", rsi, adx, w, tp), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", 3.0, "trail_activation_pct", w,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // consumer_services: R40:RSI45_ADX30_TR5_W10_TP10 → RSI 43-47, ADX 28-32, W 8-12
        for (double rsi : new double[]{43, 47}) {
            for (double adx : new double[]{28, 29, 31, 32}) {
                for (double w : new double[]{8, 12, 15}) {
                    for (double tp : new double[]{8, 10, 12}) {
                        ha(s, "R41", String.format("RSI%.0f_ADX%.0f_TR5_W%.0f_TP%.0f_CS", rsi, adx, w, tp), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", 5.0, "trail_activation_pct", w,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // fmcg: R40:TONLY_ADX27_TR4_W25_TP15 → ADX 26-28, W 22-28, TP 12-18
        for (double adx : new double[]{26, 28}) {
            for (double w : new double[]{22, 28}) {
                for (double tp : new double[]{12, 18}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR4_W%.0f_TP%.0f_FM", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 4.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }

        // financial: R40:TONLY_ADX30_TR5_W35_TP15 → ADX 29-31, W 33-37, TP 12-18
        for (double adx : new double[]{29, 31}) {
            for (double w : new double[]{33, 37, 40}) {
                for (double tp : new double[]{12, 18, 20}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR5_W%.0f_TP%.0f_FI", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 5.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }

        // healthcare: R40:TONLY_ADX27_TR5_W30_TP20 → ADX 26-28, W 28-32, TP 18-22
        for (double adx : new double[]{26, 28}) {
            for (double w : new double[]{28, 32, 35}) {
                for (double tp : new double[]{18, 22, 25}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR5_W%.0f_TP%.0f_HC", adx, w, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 5.0, "trail_activation_pct", w,
                            "tp_atr_mult", tp);
                }
            }
        }

        // metals: R40:PB_ADX20_CD50_TR5_A7_TP10 → ADX 18-22, CD 45-55, TP 8-12
        for (double adx : new double[]{18, 19, 21, 22}) {
            for (int cd : new int[]{45, 55}) {
                for (double tp : new double[]{8, 12, 15}) {
                    ha(s, "R41", String.format("PB_ADX%.0f_CD%d_TR5_A7_TP%.0f_ME", adx, cd, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 5.0, "trail_activation_pct", 7.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // power: R40:PB_ADX20_CD50_TR3_A5_TP10 → ADX 18-22, CD 40-60, TP 8-15
        for (double adx : new double[]{18, 19, 21, 22}) {
            for (int cd : new int[]{40, 45, 55, 60}) {
                for (double tp : new double[]{8, 12, 15}) {
                    ha(s, "R41", String.format("PB_ADX%.0f_CD%d_TR3_A5_TP%.0f_PW", adx, cd, tp), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "cooldown_bars", cd,
                            "trail_atr_mult", 3.0, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }

        // telecom: R40:PB12_ADX20_TR3_A7_TP10 → SMCL based, try HA equivalent
        Map<String, Object> smclPb41 = new HashMap<>();
        smclPb41.put("require_bos", false);
        smclPb41.put("entry_mode", "pullback");
        smclPb41.put("bos_entry", false);
        smclPb41.put("ha_flip_exit", false);
        smclPb41.put("trailing_stop", true);
        smclPb41.put("sl_atr_mult", 0.0);
        for (int ema : new int[]{10, 12, 14}) {
            for (double adx : new double[]{18, 20, 22}) {
                for (double tp : new double[]{8, 10, 12, 15}) {
                    smcl(s, "R41", String.format("PB%d_ADX%.0f_TR3_A7_TP%.0f_TC", ema, adx, tp), smclPb41,
                            "entry_ema", ema,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", 3.0, "trail_activation_pct", 7.0,
                            "tp_atr_mult", tp);
                }
            }
        }
        // telecom HA-based alternatives
        for (double adx : new double[]{18, 20, 22, 25}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double tp : new double[]{10, 15, 20}) {
                    ha(s, "R41", String.format("TONLY_ADX%.0f_TR%.0f_A5_TP%.0f_TC", adx, tr, tp), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                            "tp_atr_mult", tp);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R39 — SECOND-PASS SECTOR OPTIMIZATION (~500 new variants)
    //
    //  Based on R38 results gap analysis:
    //  1. PB + low ADX (10-18) is the new winning pattern — fine-tune it
    //  2. PB + cooldown (never tested): best PB entry + skip after loss
    //  3. PB + WIDE activation (never tested): PB entry + let winners run
    //  4. PB + TP cap (never tested): PB entry + lock gains
    //  5. RSI pullback + WIDE activation + TP (IT, textiles combos)
    //  6. TONLY + cooldown + WIDE (services combo)
    //  7. Realty: HA flip exit=true + tight trail (what works on 2100 stocks)
    //  8. Fine ADX sweep near winners: ±2 around winning ADX values
    //  9. PB with longer trend requirement (min_trend_bars=8,10)
    // 10. SMCL PB with entry_ema 6,15 (edge cases never tested)
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR39(Map<String, Supplier<Strategy>> s) {

        // ── Common bases ─────────────────────────────────────────

        // TONLY base: HA flip entry, NO HA flip exit, trail only
        Map<String, Object> tonlyBase = new HashMap<>();
        tonlyBase.put("entry_mode", "flip");
        tonlyBase.put("ha_flip_exit", false);
        tonlyBase.put("trailing_stop", true);
        tonlyBase.put("atr_period", 14);

        // PB TONLY base: pullback entry, trail-only exit
        Map<String, Object> pbTonly = new HashMap<>();
        pbTonly.put("entry_mode", "pullback");
        pbTonly.put("min_trend_bars", 5);
        pbTonly.put("max_pullback_bars", 2);
        pbTonly.put("ha_flip_exit", false);
        pbTonly.put("trailing_stop", true);
        pbTonly.put("atr_period", 14);

        // RSI pullback base
        Map<String, Object> rsiPbBase = new HashMap<>();
        rsiPbBase.put("entry_mode", "rsi_pullback");
        rsiPbBase.put("rsi_period", 2);
        rsiPbBase.put("ha_flip_exit", false);
        rsiPbBase.put("trailing_stop", true);
        rsiPbBase.put("atr_period", 14);

        // HA flip + flip exit + tight trail (what works on 2100 stocks)
        Map<String, Object> haFlipBase = new HashMap<>();
        haFlipBase.put("entry_mode", "flip");
        haFlipBase.put("ha_flip_exit", true);
        haFlipBase.put("trailing_stop", true);
        haFlipBase.put("atr_period", 14);

        // SMCL PB TONLY base
        Map<String, Object> smclPbTonly = new HashMap<>();
        smclPbTonly.put("require_bos", false);
        smclPbTonly.put("entry_mode", "pullback");
        smclPbTonly.put("bos_entry", false);
        smclPbTonly.put("ha_flip_exit", false);
        smclPbTonly.put("trailing_stop", true);
        smclPbTonly.put("sl_atr_mult", 0.0);

        // ═══════════════════════════════════════════════════════
        //  A. PB + FINE LOW ADX (10-18) — won capital_goods, metals, power
        //     R38 tested {15,20,25,30}. Now zoom into 10-18 range
        //     + finer trail 2.0-5.0 step 0.5
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{10, 12, 13, 14, 16, 17, 18}) {
            for (double tr : new double[]{2.0, 2.5, 3.0, 3.5, 4.0, 5.0}) {
                for (double a : new double[]{3.0, 5.0, 7.0}) {
                    ha(s, "R39", String.format("PB_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. PB + COOLDOWN (never tested combo)
        //     PB entry is selective, cooldown avoids whipsaw streaks
        //     Target: services, construction, consumer_services
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{15, 20, 25}) {
            for (int cd : new int[]{10, 20, 30, 50}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R39", String.format("PB_ADX%.0f_CD%d_TR%.0f_A%.0f", adx, cd, tr, a), pbTonly,
                                "adx_filter", true, "adx_threshold", adx,
                                "cooldown_bars", cd,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. PB + WIDE ACTIVATION (never tested combo)
        //     PB entry + ultra-wide trail activation → let PB winners run
        //     Target: financial_services, healthcare pattern on PB
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{15, 20, 25}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double a : new double[]{10.0, 15.0, 20.0, 25.0}) {
                    ha(s, "R39", String.format("PB_ADX%.0f_TR%.0f_W%.0f", adx, tr, a), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. PB + TP CAP (never tested combo)
        //     PB entry + take profit lock-in → reduce MDD
        //     Target: metals, IT, construction
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{15, 20, 25}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double a : new double[]{5.0, 7.0}) {
                    for (double tp : new double[]{10.0, 15.0, 20.0, 30.0}) {
                        ha(s, "R39", String.format("PB_ADX%.0f_TR%.0f_A%.0f_TP%.0f", adx, tr, a, tp), pbTonly,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. RSI PULLBACK + WIDE ACTIVATION + TP (IT, textiles combo)
        //     R38 RSI pullback won IT/textiles but MDD is high
        //     Add WIDE activation and TP to reduce MDD
        // ═══════════════════════════════════════════════════════
        for (double rsi : new double[]{45, 50, 55, 60}) {
            for (double adx : new double[]{20, 25, 30}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    // WIDE activation
                    for (double a : new double[]{10.0, 15.0, 20.0}) {
                        ha(s, "R39", String.format("RSI%.0f_ADX%.0f_TR%.0f_W%.0f", rsi, adx, tr, a), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                    // TP cap
                    for (double tp : new double[]{10.0, 15.0, 20.0}) {
                        ha(s, "R39", String.format("RSI%.0f_ADX%.0f_TR%.0f_TP%.0f", rsi, adx, tr, tp), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", 5.0,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. TONLY + WIDER COOLDOWN (services combo)
        //     R38 CD20 won services. Try CD30-80 + WIDE activation
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30, 33}) {
            for (int cd : new int[]{30, 40, 50, 60, 80}) {
                for (double tr : new double[]{4.0, 5.0, 7.0}) {
                    for (double a : new double[]{5.0, 7.0, 10.0}) {
                        ha(s, "R39", String.format("TONLY_ADX%.0f_CD%d_TR%.0f_A%.0f", adx, cd, tr, a), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "cooldown_bars", cd,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. REALTY SPECIAL: HA flip+exit + tight trail
        //     Nothing from R38 worked. Go back to what works on 2100 stocks:
        //     HA flip entry + HA flip exit + tight trail (0.3-1.0 ATR)
        //     + ADX filter. Also try SMCL approach.
        // ═══════════════════════════════════════════════════════
        for (double tr : new double[]{0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1.0}) {
            for (double a : new double[]{0.0, 1.0, 2.0, 3.0, 5.0}) {
                ha(s, "R39", String.format("HAEX_TR%.1f_A%.0f", tr, a), haFlipBase,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }
        // HA flip+exit + tight trail + ADX
        for (double adx : new double[]{10, 15, 20, 25}) {
            for (double tr : new double[]{0.4, 0.5, 0.7, 1.0}) {
                for (double a : new double[]{1.0, 3.0, 5.0}) {
                    ha(s, "R39", String.format("HAEX_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), haFlipBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }
        // SMCL HA flip+exit + tight trail (realty special)
        Map<String, Object> smclHaex = new HashMap<>();
        smclHaex.put("require_bos", false);
        smclHaex.put("entry_mode", "ha_flip");
        smclHaex.put("bos_entry", false);
        smclHaex.put("ha_flip_exit", true);
        smclHaex.put("trailing_stop", true);
        smclHaex.put("sl_atr_mult", 0.0);
        for (double tr : new double[]{0.4, 0.5, 0.7, 1.0}) {
            for (double a : new double[]{1.0, 3.0, 5.0}) {
                smcl(s, "R39", String.format("HAEX_TR%.1f_A%.0f", tr, a), smclHaex,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. PB + LONGER TREND (min_trend_bars=8,10)
        //     More confirmed uptrend before pullback entry → higher WR
        //     Target: construction, realty, services
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbLong = new HashMap<>();
        pbLong.put("entry_mode", "pullback");
        pbLong.put("ha_flip_exit", false);
        pbLong.put("trailing_stop", true);
        pbLong.put("atr_period", 14);
        pbLong.put("max_pullback_bars", 2);

        for (int mtb : new int[]{8, 10}) {
            for (double adx : new double[]{10, 15, 20, 25}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double a : new double[]{3.0, 5.0, 7.0}) {
                        ha(s, "R39", String.format("PBL%d_ADX%.0f_TR%.0f_A%.0f", mtb, adx, tr, a), pbLong,
                                "min_trend_bars", mtb,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  I. SMCL PB with entry_ema 6,15 (edge cases R38 missed)
        //     R38 tested 8,10,12. Try 6 (tighter) and 15 (wider)
        //     Target: capital_goods, metals, consumer_services
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{6, 15}) {
            for (double adx : new double[]{12, 15, 20, 25}) {
                for (double tr : new double[]{3.0, 4.0, 5.0}) {
                    for (double a : new double[]{3.0, 5.0, 7.0}) {
                        smcl(s, "R39", String.format("PB%d_ADX%.0f_TR%.0f_A%.0f", ema, adx, tr, a), smclPbTonly,
                                "entry_ema", ema,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  J. FINE-TUNE NEAR CURRENT WINNERS (±2 ADX, ±0.5 trail)
        //     Micro-optimize around each sector's winning parameters
        // ═══════════════════════════════════════════════════════
        // financial/healthcare winner: TONLY_ADX25_TR3_WIDE25 → try ADX 23-27, TR 2.5-3.5
        for (double adx : new double[]{23, 24, 26, 27}) {
            for (double tr : new double[]{2.5, 3.0, 3.5}) {
                for (double a : new double[]{20.0, 25.0, 30.0}) {
                    ha(s, "R39", String.format("TONLY_ADX%.0f_TR%.1f_W%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }
        // consumer_durables winner: TONLY_ADX33_TR5.0_A8_TP20 → try ADX 31-35, TR 4.5-5.5
        for (double adx : new double[]{31, 34, 35}) {
            for (double tr : new double[]{4.5, 5.0, 5.5}) {
                for (double a : new double[]{7.0, 8.0, 9.0}) {
                    for (double tp : new double[]{15.0, 20.0, 25.0}) {
                        ha(s, "R39", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f_TP%.0f", adx, tr, a, tp), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  K. REGIME + LOW ADX + COOLDOWN (construction special)
        //     R38 regime won construction. Add cooldown + lower ADX
        // ═══════════════════════════════════════════════════════
        Map<String, Object> regimeBase = new HashMap<>();
        regimeBase.put("entry_mode", "regime");
        regimeBase.put("ha_flip_exit", false);
        regimeBase.put("trailing_stop", true);
        regimeBase.put("atr_period", 14);

        for (int window : new int[]{12, 15, 20}) {
            for (double threshold : new double[]{0.7, 0.75, 0.8, 0.85}) {
                for (double rsiEntry : new double[]{35, 40, 45, 50}) {
                    for (double tr : new double[]{4.0, 5.0, 6.0}) {
                        ha(s, "R39", String.format("REG_W%d_T%.0f_R%.0f_TR%.0f", window, threshold * 100, rsiEntry, tr), regimeBase,
                                "regime_window", window, "regime_threshold", threshold,
                                "rsi_entry", rsiEntry,
                                "trail_atr_mult", tr, "trail_activation_pct", 5.0);
                    }
                }
            }
        }
        // Regime + cooldown
        for (int window : new int[]{15, 20}) {
            for (double threshold : new double[]{0.7, 0.8}) {
                for (int cd : new int[]{10, 20, 30}) {
                    for (double tr : new double[]{4.0, 5.0}) {
                        ha(s, "R39", String.format("REG_W%d_T%.0f_CD%d_TR%.0f", window, threshold * 100, cd, tr), regimeBase,
                                "regime_window", window, "regime_threshold", threshold,
                                "rsi_entry", 40.0,
                                "cooldown_bars", cd,
                                "trail_atr_mult", tr, "trail_activation_pct", 5.0);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  L. RSI PULLBACK + COOLDOWN (textiles, IT combo)
        //     RSI pullback won but MDD is high. Cooldown should help.
        // ═══════════════════════════════════════════════════════
        for (double rsi : new double[]{45, 50, 55}) {
            for (double adx : new double[]{20, 25, 30}) {
                for (int cd : new int[]{10, 20, 30}) {
                    for (double tr : new double[]{3.0, 4.0, 5.0}) {
                        ha(s, "R39", String.format("RSI%.0f_ADX%.0f_CD%d_TR%.0f", rsi, adx, cd, tr), rsiPbBase,
                                "rsi_entry", rsi,
                                "adx_filter", true, "adx_threshold", adx,
                                "cooldown_bars", cd,
                                "trail_atr_mult", tr, "trail_activation_pct", 5.0);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  R38 — DEEP SECTOR-TARGETED OPTIMIZATION (~350 new variants)
    //
    //  Goal: Beat every current sector winner's composite score.
    //
    //  Research findings from gap analysis:
    //  1. TONLY + TP cap: never tested. TP locks in gains → lower MDD
    //  2. TONLY + fine SL: R37 used coarse ADX {25,30,35}. Fine-tune SL
    //     with exact winning ADX values per sector
    //  3. RSI pullback entry + TONLY exit: enters on momentum pullback,
    //     exits only via trailing → more selective entries
    //  4. Exit EMA + trail combo: exit EMA catches trend end, trail catches
    //     intra-trade drawdowns
    //  5. HA regime mode + TONLY: regime filter = most selective entry
    //  6. Volume filter + TONLY: volume-confirmed entries reduce whipsaws
    //  7. SMCL with TONLY-style exit (ha_flip_exit=false): BOS structure
    //     for entry, trail-only for exit
    //  8. Wider activation sweeps for weak sectors (consumer_services,
    //     construction, services)
    //  9. Cross-sector pollination: apply healthcare winner pattern to
    //     weaker sectors and vice versa
    // ══════════════════════════════════════════════════════════════

    private static void addAllStockR38(Map<String, Supplier<Strategy>> s) {

        // ── Common bases ─────────────────────────────────────────

        // TONLY base: HA flip entry, NO HA flip exit, trail only
        Map<String, Object> tonlyBase = new HashMap<>();
        tonlyBase.put("entry_mode", "flip");
        tonlyBase.put("ha_flip_exit", false);
        tonlyBase.put("trailing_stop", true);
        tonlyBase.put("atr_period", 14);

        // HA base: HA flip entry + HA flip exit + trail
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("atr_period", 14);

        // RSI pullback base for HA strategy
        Map<String, Object> rsiPbBase = new HashMap<>();
        rsiPbBase.put("entry_mode", "rsi_pullback");
        rsiPbBase.put("rsi_period", 2);
        rsiPbBase.put("ha_flip_exit", false);
        rsiPbBase.put("trailing_stop", true);
        rsiPbBase.put("atr_period", 14);

        // SMCL base for TONLY-style exit
        Map<String, Object> smclTonly = new HashMap<>();
        smclTonly.put("require_bos", false);
        smclTonly.put("entry_mode", "ha_flip");
        smclTonly.put("bos_entry", false);
        smclTonly.put("ha_flip_exit", false);  // TONLY style
        smclTonly.put("trailing_stop", true);
        smclTonly.put("sl_atr_mult", 0.0);

        // ═══════════════════════════════════════════════════════
        //  A. TONLY + TP CAP (never tested before)
        //     Trail-only exit + take-profit ceiling → locks gains, cuts MDD
        //     Target: ALL sectors — especially high-MDD ones
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{27, 30, 32, 33}) {
            for (double tr : new double[]{4.0, 4.5, 5.0}) {
                for (double a : new double[]{5.0, 6.0, 8.0}) {
                    for (double tp : new double[]{15.0, 20.0, 30.0}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f_TP%.0f", adx, tr, a, tp), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "tp_atr_mult", tp);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. TONLY + FINE SL with exact sector-winning ADX values
        //     R37 tested coarse ADX {25,30,35}. Now: {27,28,31,32,33}
        //     + finer SL steps {1.5, 2.5, 3.5}
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{28, 31, 32, 33}) {
            for (double tr : new double[]{4.0, 4.5, 5.0, 5.5}) {
                for (double a : new double[]{5.0, 6.0, 7.0, 8.0}) {
                    for (double sl : new double[]{1.5, 2.5, 3.5}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_TR%.1f_A%.0f_SL%.1f", adx, tr, a, sl), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "sl_atr_mult", sl);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. RSI PULLBACK ENTRY + TONLY EXIT (new combo)
        //     RSI(2) pullback in trend → more selective entry
        //     Combined with ADX filter + trail-only exit
        //     Target: construction, services, consumer_services, realty
        // ═══════════════════════════════════════════════════════
        for (double rsiEntry : new double[]{30, 40, 50, 55}) {
            for (double adx : new double[]{15, 20, 25, 30}) {
                for (double tr : new double[]{3.0, 4.0, 5.0, 7.0}) {
                    for (double a : new double[]{3.0, 5.0, 7.0}) {
                        ha(s, "R38", String.format("RSI%.0f_ADX%.0f_TR%.0f_A%.0f", rsiEntry, adx, tr, a), rsiPbBase,
                                "rsi_entry", rsiEntry,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. EXIT EMA + TRAIL COMBO (new combo)
        //     Exit EMA catches trend end, trail catches drawdowns
        //     TONLY base + exit EMA 50/100/150
        //     Target: metals_mining, services, consumer_services
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30, 33}) {
            for (int exitEma : new int[]{50, 100, 150}) {
                for (double tr : new double[]{4.0, 5.0, 7.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_X%d_TR%.0f_A%.0f", adx, exitEma, tr, a), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "exit_ema", exitEma,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. HA REGIME MODE + TONLY (never sector-tested)
        //     Regime = count bullish HA bars in window → most selective
        //     Target: weak sectors that need fewer, higher-quality trades
        // ═══════════════════════════════════════════════════════
        Map<String, Object> regimeBase = new HashMap<>();
        regimeBase.put("entry_mode", "regime");
        regimeBase.put("ha_flip_exit", false);
        regimeBase.put("trailing_stop", true);
        regimeBase.put("atr_period", 14);

        for (int window : new int[]{10, 15, 20}) {
            for (double threshold : new double[]{0.6, 0.7, 0.8}) {
                for (double rsiEntry : new double[]{40, 55, 70}) {
                    for (double tr : new double[]{4.0, 5.0, 7.0}) {
                        ha(s, "R38", String.format("REG_W%d_T%.0f_RSI%.0f_TR%.0f", window, threshold * 100, rsiEntry, tr), regimeBase,
                                "regime_window", window, "regime_threshold", threshold,
                                "rsi_entry", rsiEntry,
                                "trail_atr_mult", tr, "trail_activation_pct", 5.0);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. VOLUME FILTER + TONLY (reduce whipsaws)
        //     Entry only when volume > N × average
        //     Target: consumer_services (low WR), textiles, realty
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30, 33}) {
            for (double volMult : new double[]{1.2, 1.5, 2.0}) {
                for (double tr : new double[]{4.0, 5.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_VOL%.0f_TR%.0f_A%.0f", adx, volMult * 10, tr, a), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "volume_filter", true, "vol_mult", volMult, "vol_period", 20,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. SMCL WITH TONLY-STYLE EXIT (new architecture)
        //     BOS structure for regime, HA flip for entry, trail-only exit
        //     No HA flip exit = let winners run longer
        //     Target: capital_goods, metals_mining, IT
        // ═══════════════════════════════════════════════════════
        for (double tr : new double[]{3.0, 4.0, 5.0, 7.0}) {
            for (double a : new double[]{3.0, 5.0, 7.0, 10.0}) {
                smcl(s, "R38", String.format("TONLY_TR%.0f_A%.0f", tr, a), smclTonly,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // SMCL TONLY + ADX filter
        for (double adx : new double[]{15, 20, 25, 30}) {
            for (double tr : new double[]{4.0, 5.0, 7.0}) {
                for (double a : new double[]{5.0, 7.0}) {
                    smcl(s, "R38", String.format("TONLY_ADX%.0f_TR%.0f_A%.0f", adx, tr, a), smclTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // SMCL TONLY + BOS exit (structure break exits, no HA flip)
        for (double tr : new double[]{4.0, 5.0, 7.0}) {
            for (double a : new double[]{5.0, 7.0, 10.0}) {
                smcl(s, "R38", String.format("BOSX_TONLY_TR%.0f_A%.0f", tr, a), smclTonly,
                        "bos_exit", true, "swing_left", 20, "swing_right", 3,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. ULTRA-WIDE ACTIVATION (for weak sectors)
        //     Activation 10-25% → only trail after massive gains
        //     Lets winners run, accepts bigger drawdowns on losers
        //     Target: construction, consumer_services, services
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30, 33}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double a : new double[]{10.0, 15.0, 20.0, 25.0}) {
                    ha(s, "R38", String.format("TONLY_ADX%.0f_TR%.0f_WIDE%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  I. CROSS-SECTOR POLLINATION
        //     Apply healthcare winner (best scorer: ADX33_TR4.5_A8) to ALL
        //     Apply consumer_durables winner (ADX27_TR5.0_A6) to ALL
        //     Apply realty winner pattern (RSI+HEB+VOL) to ALL
        //     Fine-tune each with SL, TP, exit EMA
        // ═══════════════════════════════════════════════════════

        // Healthcare pattern → all sectors (fine ADX sweep)
        for (double adx : new double[]{30, 31, 32, 33, 34, 35}) {
            for (double tr : new double[]{4.5, 5.0, 5.5}) {
                for (double a : new double[]{7.0, 8.0, 9.0, 10.0}) {
                    ha(s, "R38", String.format("HC_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // Consumer durables pattern → all sectors (lower ADX)
        for (double adx : new double[]{22, 25, 27, 29}) {
            for (double tr : new double[]{4.5, 5.0, 5.5, 6.0}) {
                for (double a : new double[]{5.0, 6.0, 7.0, 8.0}) {
                    ha(s, "R38", String.format("CD_ADX%.0f_TR%.1f_A%.0f", adx, tr, a), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  J. PULLBACK ENTRY + TONLY EXIT (HA pullback mode)
        //     Enter after brief bearish pullback in bullish trend
        //     NO HA flip exit → trail only
        //     Target: capital_goods, metals_mining, construction
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbTonly = new HashMap<>();
        pbTonly.put("entry_mode", "pullback");
        pbTonly.put("min_trend_bars", 5);
        pbTonly.put("max_pullback_bars", 2);
        pbTonly.put("ha_flip_exit", false);
        pbTonly.put("trailing_stop", true);
        pbTonly.put("atr_period", 14);

        for (double adx : new double[]{15, 20, 25, 30}) {
            for (double tr : new double[]{3.0, 4.0, 5.0, 7.0}) {
                for (double a : new double[]{2.0, 3.0, 5.0, 7.0}) {
                    ha(s, "R38", String.format("PB_TONLY_ADX%.0f_TR%.0f_A%.0f", adx, tr, a), pbTonly,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  K. TONLY + BB WIDTH FILTER (skip sideways markets)
        //     Only enter when BB width > threshold → volatile = profitable
        //     Target: consumer_services (41% WR), construction
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30, 33}) {
            for (double bbMin : new double[]{4.0, 6.0, 8.0}) {
                for (double tr : new double[]{4.0, 5.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_BB%.0f_TR%.0f_A%.0f", adx, bbMin, tr, a), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "bb_filter", true, "bb_min_width", bbMin,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  L. HA FLIP EXIT + TONLY ADX combos (hybrid)
        //     Keep HA flip exit active but also add ADX filter
        //     Combines quick HA exits + selective entries
        //     Target: sectors where pure TONLY is too aggressive
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 28, 30, 33}) {
            for (double tr : new double[]{3.0, 4.0, 5.0}) {
                for (double a : new double[]{3.0, 5.0, 7.0}) {
                    ha(s, "R38", String.format("HAEX_ADX%.0f_TR%.0f_A%.0f", adx, tr, a), haBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trail_atr_mult", tr, "trail_activation_pct", a);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  M. TONLY + COOLDOWN (reduce consecutive losses)
        //     Skip N bars after a losing trade
        //     Target: high-MDD sectors (IT, metals, auto)
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{27, 30, 33}) {
            for (int cooldown : new int[]{5, 10, 20}) {
                for (double tr : new double[]{4.0, 5.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R38", String.format("TONLY_ADX%.0f_CD%d_TR%.0f_A%.0f", adx, cooldown, tr, a), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "cooldown_bars", cooldown,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  N. TONLY + TREND EMA + ADX (double trend filter)
        //     Price above EMA AND ADX above threshold
        //     Ultra-selective → should boost WR and PF
        //     Target: consumer_services, construction, realty
        // ═══════════════════════════════════════════════════════
        for (int ema : new int[]{50, 100, 200}) {
            for (double adx : new double[]{20, 25, 30}) {
                for (double tr : new double[]{4.0, 5.0, 7.0}) {
                    for (double a : new double[]{5.0, 7.0}) {
                        ha(s, "R38", String.format("TONLY_E%d_ADX%.0f_TR%.0f_A%.0f", ema, adx, tr, a), tonlyBase,
                                "trend_ema", ema,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  O. SMCL PULLBACK + TONLY EXIT + ADX (new architecture)
        //     SMCLite pullback entry (dip below entry EMA, recover)
        //     + trail-only exit + ADX filter
        //     Target: capital_goods, metals_mining
        // ═══════════════════════════════════════════════════════
        Map<String, Object> smclPbTonly = new HashMap<>();
        smclPbTonly.put("require_bos", false);
        smclPbTonly.put("entry_mode", "pullback");
        smclPbTonly.put("bos_entry", false);
        smclPbTonly.put("ha_flip_exit", false);
        smclPbTonly.put("trailing_stop", true);
        smclPbTonly.put("sl_atr_mult", 0.0);

        for (int entryEma : new int[]{8, 10, 12}) {
            for (double adx : new double[]{15, 20, 25}) {
                for (double tr : new double[]{3.0, 4.0, 5.0, 7.0}) {
                    for (double a : new double[]{3.0, 5.0, 7.0}) {
                        smcl(s, "R38", String.format("PB%d_TONLY_ADX%.0f_TR%.0f_A%.0f", entryEma, adx, tr, a), smclPbTonly,
                                "entry_ema", entryEma,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a);
                    }
                }
            }
        }
    }

    private static void addAllStockR37(Map<String, Supplier<Strategy>> s) {

        // ═══════════════════════════════════════════════════════
        //  A. HA + TONLY + ADX + HARD STOP
        //     Apply to: FMCG, Healthcare, Financial, Oil Gas, Chemicals,
        //               Telecom, Power winners
        // ═══════════════════════════════════════════════════════
        Map<String, Object> tonlyBase = new HashMap<>();
        tonlyBase.put("entry_mode", "flip");
        tonlyBase.put("ha_flip_exit", false);
        tonlyBase.put("trailing_stop", true);
        tonlyBase.put("atr_period", 14);

        // TONLY winners + ATR stops (slash MDD)
        for (double adx : new double[]{25, 30, 35}) {
            for (double tr : new double[]{4.0, 5.0, 7.0}) {
                for (double a : new double[]{5.0, 7.0}) {
                    for (double sl : new double[]{2.0, 3.0, 4.0}) {
                        ha(s, "R37", String.format("TONLY_ADX%.0f_TR%.0f_A%.0f_SL%.1f", adx, tr, a, sl), tonlyBase,
                                "adx_filter", true, "adx_threshold", adx,
                                "trail_atr_mult", tr, "trail_activation_pct", a,
                                "sl_atr_mult", sl);
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  B. HA TONLY_TR5_A5 + ATR stop (FMCG, Healthcare winners)
        // ═══════════════════════════════════════════════════════
        for (double tr : new double[]{4.0, 5.0, 6.0}) {
            for (double a : new double[]{3.0, 5.0}) {
                for (double sl : new double[]{2.0, 3.0, 4.0}) {
                    ha(s, "R37", String.format("TONLY_TR%.0f_A%.0f_SL%.1f", tr, a, sl), tonlyBase,
                            "trail_atr_mult", tr, "trail_activation_pct", a,
                            "sl_atr_mult", sl);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C. HA flip + EMA + slope + hard stop (IT, Capital Goods)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> haBase = new HashMap<>();
        haBase.put("entry_mode", "flip");
        haBase.put("ha_flip_exit", true);
        haBase.put("trailing_stop", true);
        haBase.put("trail_atr_mult", 0.5);
        haBase.put("trail_activation_pct", 3.0);
        haBase.put("atr_period", 14);

        for (int ema : new int[]{50, 100}) {
            for (int slope : new int[]{5, 10}) {
                for (double sl : new double[]{2.0, 3.0}) {
                    ha(s, "R37", String.format("E%d_SLP%d_SL%.0f", ema, slope, sl), haBase,
                            "trend_ema", ema, "ema_slope_bars", slope,
                            "sl_atr_mult", sl);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D. Wide-activation HA + hard stop (Capital Goods winner)
        // ═══════════════════════════════════════════════════════
        for (double a : new double[]{10.0, 12.0, 14.0, 15.0, 18.0}) {
            for (double tr : new double[]{0.5, 0.7}) {
                for (double sl : new double[]{2.0, 3.0, 4.0}) {
                    ha(s, "R37", String.format("WIDE_A%.0f_TR%.1f_SL%.0f", a, tr, sl), haBase,
                            "trail_activation_pct", a, "trail_atr_mult", tr, "sl_atr_mult", sl);
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E. SMCL_R4 base + hard stops + breakeven (drop MDD on baseline)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> smclBase = new HashMap<>();
        smclBase.put("require_bos", false);
        smclBase.put("entry_mode", "ha_flip");
        smclBase.put("bos_entry", false);
        smclBase.put("ha_flip_exit", true);
        smclBase.put("trailing_stop", true);
        smclBase.put("trail_atr_mult", 0.5);
        smclBase.put("trail_activation_pct", 3.0);
        smclBase.put("bos_exit", true);
        smclBase.put("swing_left", 20);
        smclBase.put("swing_right", 3);
        smclBase.put("atr_period", 14);

        // Hard ATR stops
        for (double sl : new double[]{1.5, 2.0, 2.5, 3.0}) {
            smcl(s, "R37", String.format("SMCL_SL%.1f", sl), smclBase, "sl_atr_mult", sl);
        }
        // Breakeven (move SL to entry after profit)
        for (double be : new double[]{2.0, 3.0, 5.0}) {
            smcl(s, "R37", String.format("SMCL_BE%.0f", be), smclBase, "breakeven_pct", be);
        }
        // Max % loss cap
        for (double ml : new double[]{3.0, 5.0, 7.0, 10.0}) {
            smcl(s, "R37", String.format("SMCL_ML%.0f", ml), smclBase, "max_loss_pct", ml);
        }
        // Combined: ATR SL + Breakeven
        for (double sl : new double[]{2.0, 3.0}) {
            for (double be : new double[]{3.0, 5.0}) {
                smcl(s, "R37", String.format("SMCL_SL%.0f_BE%.0f", sl, be), smclBase,
                        "sl_atr_mult", sl, "breakeven_pct", be);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F. PB + hard stop (Auto sector)
        // ═══════════════════════════════════════════════════════
        Map<String, Object> pbBase = new HashMap<>(smclBase);
        pbBase.put("entry_mode", "pullback");
        for (int eema : new int[]{8, 10, 12}) {
            for (double sl : new double[]{2.0, 3.0}) {
                smcl(s, "R37", String.format("PB%d_SL%.0f", eema, sl), pbBase,
                        "entry_ema", eema, "sl_atr_mult", sl);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  G. Momentum (Realty winner) + hard stop
        // ═══════════════════════════════════════════════════════
        Map<String, Object> momBase = new HashMap<>();
        momBase.put("strategy_type", "dual_mom");
        momBase.put("atr_period", 14);

        for (int lb : new int[]{63, 126, 252}) {
            for (double sl : new double[]{2.0, 3.0, 4.0}) {
                quant(s, "R37", String.format("MOM%d_SL%.0f", lb, sl), momBase,
                        "mom_lookback", lb, "mom_ema", 20, "sl_atr_mult", sl);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  H. ULTIMATE: TONLY + ADX + EMA + SL (all best filters + cap loss)
        // ═══════════════════════════════════════════════════════
        for (double adx : new double[]{25, 30}) {
            for (int ema : new int[]{50, 100, 200}) {
                for (double sl : new double[]{2.0, 3.0}) {
                    ha(s, "R37", String.format("ULT_ADX%.0f_E%d_SL%.0f", adx, ema, sl), tonlyBase,
                            "adx_filter", true, "adx_threshold", adx,
                            "trend_ema", ema,
                            "trail_atr_mult", 5.0, "trail_activation_pct", 5.0,
                            "sl_atr_mult", sl);
                }
            }
        }
    }

    private static void addAllStockR27(Map<String, Supplier<Strategy>> s) {

        // ── R26 findings:
        // TP3.0:     CAGR +119%, MDD -37.71%, PF 1.40, WR 52.4% (best CAGR+MDD+WR)
        // RSI4_TP5:  CAGR +76%,  MDD -36.07%, PF 1.41, WR 50.6% (best MDD)
        // RSI3_TP5:  CAGR +89%,  MDD -38.47%, PF 1.40, WR 49.7%
        Map<String, Object> base = new HashMap<>();
        base.put("require_bos", false);
        base.put("trend_ema", 0);
        base.put("entry_mode", "rsi");
        base.put("rsi_period", 2);
        base.put("rsi_threshold", 72.0);
        base.put("bos_entry", false);
        base.put("ha_flip_exit", true);
        base.put("ha_exit_bars", 1);
        base.put("sl_atr_mult", 0.0);
        base.put("trailing_stop", true);
        base.put("trail_atr_mult", 0.50);
        base.put("trail_activation_pct", 0.50);
        base.put("atr_period", 20);
        base.put("swing_left", 20);
        base.put("swing_right", 2);
        base.put("bos_exit", true);

        // References
        smcl(s, "R27", "TP9_REF", base, "tp_pct", 9.0);
        smcl(s, "R27", "TP3_REF", base, "tp_pct", 3.0);

        // ═══════════════════════════════════════════════════════
        //  A: Very tight TP sweep (1-4%) with RSI(2)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{1.5, 2.0, 2.5, 3.5, 4.0}) {
            smcl(s, "R27", String.format("TP%.1f", tp), base, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  B: TP3 + HEB2 (boost PF from 1.40)
        // ═══════════════════════════════════════════════════════

        smcl(s, "R27", "TP3_HEB2", base, "tp_pct", 3.0, "ha_exit_bars", 2);
        smcl(s, "R27", "TP3_HEB0", base, "tp_pct", 3.0, "ha_exit_bars", 0);
        smcl(s, "R27", "TP2_HEB2", base, "tp_pct", 2.0, "ha_exit_bars", 2);

        // ═══════════════════════════════════════════════════════
        //  C: TP3 + trail combos
        // ═══════════════════════════════════════════════════════

        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40}) {
            smcl(s, "R27", String.format("TP3_TR%.2f", tr), base, "tp_pct", 3.0, "trail_atr_mult", tr);
        }

        // ═══════════════════════════════════════════════════════
        //  D: RSI(4) + TP sweep (best MDD direction from R26)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{2, 3, 4, 5, 6}) {
            smcl(s, "R27", String.format("RSI4_TP%.0f", tp), base, "rsi_period", 4, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  E: RSI(3) + TP sweep
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{2, 3, 4, 5}) {
            smcl(s, "R27", String.format("RSI3_TP%.0f", tp), base, "rsi_period", 3, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  F: RSI(4) + TP3 + combos (max MDD improvement)
        // ═══════════════════════════════════════════════════════

        smcl(s, "R27", "RSI4_TP3_HEB2", base, "rsi_period", 4, "tp_pct", 3.0, "ha_exit_bars", 2);
        smcl(s, "R27", "RSI4_TP3_TR0.35", base, "rsi_period", 4, "tp_pct", 3.0, "trail_atr_mult", 0.35);
        smcl(s, "R27", "RSI4_TP5_HEB2", base, "rsi_period", 4, "tp_pct", 5.0, "ha_exit_bars", 2);
        smcl(s, "R27", "RSI4_TP5_TR0.35", base, "rsi_period", 4, "tp_pct", 5.0, "trail_atr_mult", 0.35);

        // ═══════════════════════════════════════════════════════
        //  G: RSI(4) threshold sweep with TP3
        // ═══════════════════════════════════════════════════════

        for (double t : new double[]{65, 68, 70, 75, 80}) {
            smcl(s, "R27", String.format("RSI4_T%.0f_TP3", t), base,
                    "rsi_period", 4, "rsi_threshold", t, "tp_pct", 3.0);
        }
    }

    private static void addAllStockR26(Map<String, Supplier<Strategy>> s) {

        // ── From R24: TP5 had best CAGR (+105%) AND best MDD (-41.50%)
        //    but PF was 1.38 (vs 1.40 for TP9). Try to boost PF with combos.
        //    Also: HEB2 boosted PF to 1.42 and WR to 48.5%.
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "rsi");
        best.put("rsi_period", 2);
        best.put("rsi_threshold", 72.0);
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("ha_exit_bars", 1);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.50);
        best.put("trail_activation_pct", 0.50);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        best.put("bos_exit", true);

        // References
        smcl(s, "R26", "TP9_REF", best, "tp_pct", 9.0);   // R25 best
        smcl(s, "R26", "TP5_REF", best, "tp_pct", 5.0);   // R24 best CAGR/MDD

        // ═══════════════════════════════════════════════════════
        //  A: TP5 + HEB2 (boost PF/WR while keeping TP5's CAGR/MDD)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{4, 5, 6, 7}) {
            smcl(s, "R26", String.format("TP%.0f_HEB2", tp), best, "tp_pct", tp, "ha_exit_bars", 2);
        }

        // ═══════════════════════════════════════════════════════
        //  B: TP5 + tighter trail (TR0.30-0.35 improved MDD in R25)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{5, 6, 7}) {
            smcl(s, "R26", String.format("TP%.0f_TR0.30", tp), best, "tp_pct", tp, "trail_atr_mult", 0.30);
            smcl(s, "R26", String.format("TP%.0f_TR0.35", tp), best, "tp_pct", tp, "trail_atr_mult", 0.35);
        }

        // ═══════════════════════════════════════════════════════
        //  C: TP5 + trail + HEB2 triple combo
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{5, 6}) {
            smcl(s, "R26", String.format("TP%.0f_TR0.35_HEB2", tp), best,
                    "tp_pct", tp, "trail_atr_mult", 0.35, "ha_exit_bars", 2);
            smcl(s, "R26", String.format("TP%.0f_TR0.30_HEB2", tp), best,
                    "tp_pct", tp, "trail_atr_mult", 0.30, "ha_exit_bars", 2);
        }

        // ═══════════════════════════════════════════════════════
        //  D: RSI period sweep with TP5-9 (RSI(3), RSI(4))
        // ═══════════════════════════════════════════════════════

        for (int rp : new int[]{3, 4}) {
            for (double tp : new double[]{5, 7, 9}) {
                smcl(s, "R26", String.format("RSI%d_TP%.0f", rp, tp), best,
                        "rsi_period", rp, "tp_pct", tp);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E: Fine-tune TP3-5 range (sub-5% TP)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{3, 3.5, 4, 4.5}) {
            smcl(s, "R26", String.format("TP%.1f", tp), best, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  F: TP5 + RSI threshold sweep (lower threshold = more entries)
        // ═══════════════════════════════════════════════════════

        for (double t : new double[]{65, 68, 70, 75, 80}) {
            smcl(s, "R26", String.format("TP5_T%.0f", t), best, "tp_pct", 5, "rsi_threshold", t);
        }

        // ═══════════════════════════════════════════════════════
        //  G: TP5 + immediate trail activation
        // ═══════════════════════════════════════════════════════

        smcl(s, "R26", "TP5_A0", best, "tp_pct", 5, "trail_activation_pct", 0.0);
        smcl(s, "R26", "TP5_A0_TR0.35", best, "tp_pct", 5, "trail_activation_pct", 0.0, "trail_atr_mult", 0.35);
    }

    private static void addAllStockR25(Map<String, Supplier<Strategy>> s) {

        // ── Best config from R24: TP9 beats TP10 on ALL metrics ──
        // TP10: CAGR +82.83%, MDD -42.98%, PF 1.40, WR 45.5%
        // TP9:  CAGR +86.51%, MDD -42.72%, PF 1.40, WR 45.9%
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "rsi");
        best.put("rsi_period", 2);
        best.put("rsi_threshold", 72.0);
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("ha_exit_bars", 1);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.50);
        best.put("trail_activation_pct", 0.50);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        best.put("bos_exit", true);
        best.put("tp_pct", 9.0);
        smcl(s, "R25", "REF", best);  // TP9 baseline

        // ═══════════════════════════════════════════════════════
        //  A: Trail mult sweep — TR0.30-0.35 had best MDD+WR in R24
        // ═══════════════════════════════════════════════════════

        for (double tr : new double[]{0.25, 0.30, 0.35, 0.40, 0.45, 0.55}) {
            smcl(s, "R25", String.format("TR%.2f", tr), best, "trail_atr_mult", tr);
        }

        // ═══════════════════════════════════════════════════════
        //  B: Trail activation sweep — A0.00 had lower MDD
        // ═══════════════════════════════════════════════════════

        for (double a : new double[]{0.0, 0.10, 0.20, 0.25, 0.30, 0.40}) {
            smcl(s, "R25", String.format("A%.2f", a), best, "trail_activation_pct", a);
        }

        // ═══════════════════════════════════════════════════════
        //  C: Combined TP9 + best trail + activation combos
        // ═══════════════════════════════════════════════════════

        // TR0.30-0.40 × A0.00-0.25 (best MDD/WR combos from R24)
        for (double tr : new double[]{0.30, 0.35, 0.40}) {
            for (double a : new double[]{0.0, 0.10, 0.25}) {
                smcl(s, "R25", String.format("TR%.2f_A%.2f", tr, a), best,
                        "trail_atr_mult", tr, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  D: TP8.5-9.5 fine-tuning (0.5 step)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{8.0, 8.5, 9.5, 10.0}) {
            smcl(s, "R25", String.format("TP%.1f", tp), best, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  E: TP9 + HEB variations — HEB2 had best PF/WR
        // ═══════════════════════════════════════════════════════

        smcl(s, "R25", "HEB0", best, "ha_exit_bars", 0);
        smcl(s, "R25", "HEB2", best, "ha_exit_bars", 2);

        // ═══════════════════════════════════════════════════════
        //  F: Combined: TP9 + TR0.35 + HEB2 (max PF/WR)
        //     and TP9 + TR0.35 + A0.00 (max MDD)
        // ═══════════════════════════════════════════════════════

        smcl(s, "R25", "TR0.35_HEB2", best, "trail_atr_mult", 0.35, "ha_exit_bars", 2);
        smcl(s, "R25", "TR0.35_A0.00", best, "trail_atr_mult", 0.35, "trail_activation_pct", 0.0);
        smcl(s, "R25", "TR0.30_HEB2", best, "trail_atr_mult", 0.30, "ha_exit_bars", 2);
        smcl(s, "R25", "TR0.30_A0.00", best, "trail_atr_mult", 0.30, "trail_activation_pct", 0.0);

        // ═══════════════════════════════════════════════════════
        //  G: Best TP + trail + activation triple combo
        // ═══════════════════════════════════════════════════════

        smcl(s, "R25", "TR0.35_A0.00_HEB2", best,
                "trail_atr_mult", 0.35, "trail_activation_pct", 0.0, "ha_exit_bars", 2);
        smcl(s, "R25", "TR0.30_A0.00_HEB2", best,
                "trail_atr_mult", 0.30, "trail_activation_pct", 0.0, "ha_exit_bars", 2);
        smcl(s, "R25", "TR0.35_A0.10_HEB2", best,
                "trail_atr_mult", 0.35, "trail_activation_pct", 0.10, "ha_exit_bars", 2);
    }

    private static void addAllStockR24(Map<String, Supplier<Strategy>> s) {

        // ── Best config from R23: TP10 wins ALL metrics vs REF ──
        // REF: CAGR +12.02%, MDD -45.04%, PF 1.30, WR 44.5%
        // TP10: CAGR +82.83%, MDD -42.98%, PF 1.40, WR 45.5%
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "rsi");
        best.put("rsi_period", 2);
        best.put("rsi_threshold", 72.0);
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("ha_exit_bars", 1);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.50);
        best.put("trail_activation_pct", 0.50);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        best.put("bos_exit", true);
        best.put("tp_pct", 10.0);
        smcl(s, "R24", "REF", best);  // TP10 baseline

        // ═══════════════════════════════════════════════════════
        //  A: Fine-tune TP% around 10 (sweet spot search)
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{5, 6, 7, 8, 9, 11, 12, 13, 14}) {
            smcl(s, "R24", String.format("TP%.0f", tp), best, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  B: Trail mult sweep with TP10 — find optimal trail
        // ═══════════════════════════════════════════════════════

        for (double tr : new double[]{0.30, 0.35, 0.40, 0.45, 0.55, 0.60, 0.70}) {
            smcl(s, "R24", String.format("TR%.2f", tr), best, "trail_atr_mult", tr);
        }

        // ═══════════════════════════════════════════════════════
        //  C: Trail activation sweep with TP10
        // ═══════════════════════════════════════════════════════

        for (double a : new double[]{0.0, 0.25, 0.30, 0.40, 0.75, 1.0}) {
            smcl(s, "R24", String.format("A%.2f", a), best, "trail_activation_pct", a);
        }

        // ═══════════════════════════════════════════════════════
        //  D: HEB variations with TP10
        // ═══════════════════════════════════════════════════════

        smcl(s, "R24", "HEB0", best, "ha_exit_bars", 0);
        smcl(s, "R24", "HEB2", best, "ha_exit_bars", 2);
        smcl(s, "R24", "HEB3", best, "ha_exit_bars", 3);
        smcl(s, "R24", "noHA", best, "ha_flip_exit", false);

        // ═══════════════════════════════════════════════════════
        //  E: BOS exit on/off with TP10
        // ═══════════════════════════════════════════════════════

        smcl(s, "R24", "noBOS", best, "bos_exit", false);

        // ═══════════════════════════════════════════════════════
        //  F: RSI threshold fine-tune with TP10
        // ═══════════════════════════════════════════════════════

        for (double t : new double[]{65, 68, 70, 74, 75, 78, 80}) {
            smcl(s, "R24", String.format("T%.0f", t), best, "rsi_threshold", t);
        }

        // ═══════════════════════════════════════════════════════
        //  G: ATR period sweep with TP10
        // ═══════════════════════════════════════════════════════

        for (int atr : new int[]{10, 14, 25, 30}) {
            smcl(s, "R24", String.format("ATR%d", atr), best, "atr_period", atr);
        }

        // ═══════════════════════════════════════════════════════
        //  H: Swing params with TP10
        // ═══════════════════════════════════════════════════════

        smcl(s, "R24", "SW15_2", best, "swing_left", 15);
        smcl(s, "R24", "SW25_2", best, "swing_left", 25);
        smcl(s, "R24", "SW20_3", best, "swing_right", 3);
        smcl(s, "R24", "SW20_1", best, "swing_right", 1);

        // ═══════════════════════════════════════════════════════
        //  I: Combined best candidates (TP10 + tuned params)
        // ═══════════════════════════════════════════════════════

        // TP8 + tighter trail
        smcl(s, "R24", "TP8_TR0.40", best, "tp_pct", 8, "trail_atr_mult", 0.40);
        smcl(s, "R24", "TP8_TR0.35", best, "tp_pct", 8, "trail_atr_mult", 0.35);
        // TP12 + wider trail
        smcl(s, "R24", "TP12_TR0.55", best, "tp_pct", 12, "trail_atr_mult", 0.55);
        smcl(s, "R24", "TP12_TR0.60", best, "tp_pct", 12, "trail_atr_mult", 0.60);
        // TP10 + no trail activation (immediate)
        smcl(s, "R24", "TP10_A0_TR0.40", best, "trail_activation_pct", 0.0, "trail_atr_mult", 0.40);
        // TP10 + HEB2 (best PF/WR from R23)
        smcl(s, "R24", "TP10_HEB2", best, "ha_exit_bars", 2);
        // TP10 + no BOS exit + HEB2
        smcl(s, "R24", "TP10_noBOS_HEB2", best, "bos_exit", false, "ha_exit_bars", 2);
    }

    private static void addAllStockR23(Map<String, Supplier<Strategy>> s) {

        // ── Best config from R22 ──
        Map<String, Object> best = new HashMap<>();
        best.put("require_bos", false);
        best.put("trend_ema", 0);
        best.put("entry_mode", "rsi");
        best.put("rsi_period", 2);
        best.put("rsi_threshold", 72.0);
        best.put("bos_entry", false);
        best.put("ha_flip_exit", true);
        best.put("ha_exit_bars", 1);
        best.put("sl_atr_mult", 0.0);
        best.put("trailing_stop", true);
        best.put("trail_atr_mult", 0.50);
        best.put("trail_activation_pct", 0.50);
        best.put("atr_period", 20);
        best.put("swing_left", 20);
        best.put("swing_right", 2);
        best.put("bos_exit", true);
        smcl(s, "R23", "REF", best);  // reference

        // ═══════════════════════════════════════════════════════
        //  A: TAKE PROFIT — lock in gains at X% (intra-bar via engine)
        //     Should ↑WR, ↑PF, may ↓CAGR if too tight
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{10, 15, 20, 25, 30, 40, 50, 60, 80, 100}) {
            smcl(s, "R23", String.format("TP%.0f", tp), best, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  B: TP + trail combos — TP caps upside, trail protects downside
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{20, 30, 40, 50}) {
            for (double tr : new double[]{0.40, 0.45, 0.55}) {
                smcl(s, "R23", String.format("TP%.0f_TR%.2f", tp, tr), best,
                        "tp_pct", tp, "trail_atr_mult", tr);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  C: TP + HEB variations — TP locks profit, HEB filters exit
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{20, 30, 50}) {
            smcl(s, "R23", String.format("TP%.0f_HEB0", tp), best,
                    "tp_pct", tp, "ha_exit_bars", 0);
            smcl(s, "R23", String.format("TP%.0f_HEB2", tp), best,
                    "tp_pct", tp, "ha_exit_bars", 2);
        }

        // ═══════════════════════════════════════════════════════
        //  D: TP + activation combos
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{30, 50}) {
            for (double a : new double[]{0.0, 0.25, 1.0}) {
                smcl(s, "R23", String.format("TP%.0f_A%.2f", tp, a), best,
                        "tp_pct", tp, "trail_activation_pct", a);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  E: TP + ML (max loss) — bounded risk/reward
        // ═══════════════════════════════════════════════════════

        for (double tp : new double[]{20, 30, 50}) {
            for (double ml : new double[]{15, 20}) {
                smcl(s, "R23", String.format("TP%.0f_ML%.0f", tp, ml), best,
                        "tp_pct", tp, "max_loss_pct", ml);
            }
        }

        // ═══════════════════════════════════════════════════════
        //  F: TP on HA flip entry (compare entry modes with TP)
        // ═══════════════════════════════════════════════════════

        Map<String, Object> haTP = new HashMap<>(best);
        haTP.put("entry_mode", "ha_flip");
        haTP.put("trail_atr_mult", 0.50);
        haTP.put("trail_activation_pct", 1.0);
        haTP.put("ha_exit_bars", 0);
        for (double tp : new double[]{20, 30, 40, 50, 80}) {
            smcl(s, "R23", String.format("HA_TP%.0f", tp), haTP, "tp_pct", tp);
        }

        // ═══════════════════════════════════════════════════════
        //  G: Best TP + RSI threshold fine-tune
        // ═══════════════════════════════════════════════════════

        for (double t : new double[]{68, 70, 74, 75}) {
            smcl(s, "R23", String.format("T%.0f_TP30", t), best,
                    "rsi_threshold", t, "tp_pct", 30);
            smcl(s, "R23", String.format("T%.0f_TP50", t), best,
                    "rsi_threshold", t, "tp_pct", 50);
        }
    }
}
