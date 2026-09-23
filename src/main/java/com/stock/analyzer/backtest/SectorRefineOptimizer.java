package com.stock.analyzer.backtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.analyzer.backtest.SectorAllPhasesOptimizer.VariantResult;
import com.stock.analyzer.backtest.config.BacktestConfig;
import com.stock.analyzer.backtest.config.Market;
import com.stock.analyzer.backtest.config.RefinedVariants;
import com.stock.analyzer.backtest.config.VariantRegistry;
import com.stock.analyzer.backtest.data.DataFetcher;
import com.stock.analyzer.backtest.model.Bar;
import com.stock.analyzer.backtest.strategy.Strategy;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Refinement search that tries to BEAT the per-sector winners produced by
 * {@link SectorAllPhasesOptimizer}. The base optimizer sweeps a coarse grid of
 * ~10k registry variants; this class runs a fine-grained <b>coordinate /
 * pattern search</b> around each sector's current champion — perturbing one
 * parameter at a time (ADX, trailing-stop, activation window, take-profit,
 * RSI, cooldown, regime, structural toggles), keeping any candidate that scores
 * higher on the exact same composite, then re-centering and shrinking the step
 * size. It loops until no sector improves at the finest step (convergence).
 *
 * <p>Winners that beat the registry champion are written to
 * {@code refined_variants.json} (name → class + params) and promoted into
 * {@code new_winners.csv}. {@link RefinedVariants} makes them resolvable by the
 * report generator and live scanner.
 *
 * <p>Run:  {@code java -Dmarket=us -cp <jar> ...SectorRefineOptimizer}
 */
public class SectorRefineOptimizer {

    private static final double EPS = 0.0005;      // min composite gain to accept
    private static final double MIN_SCALE = 0.2;   // stop shrinking below this
    private static final int    MAX_ROUNDS = 80;   // safety cap
    private static final int    MIN_BARS = 50;

    // key, baseStep, lo, hi, isInt
    private static final Object[][] TUNABLES = {
        {"adx_threshold",        2.0,  8.0,  45.0, false},
        {"trail_atr_mult",       0.5,  1.0,  8.0,  false},
        {"trail_activation_pct", 5.0,  0.0,  60.0, false},
        {"tp_atr_mult",          5.0,  0.0,  40.0, false},
        {"rsi_entry",            3.0,  30.0, 80.0, false},
        {"cooldown_bars",        10.0, 0.0,  90.0, true},
        {"regime_threshold",     0.05, 0.50, 0.95, false},
        {"regime_window",        3.0,  5.0,  30.0, true},
        {"min_trend_bars",       1.0,  2.0,  12.0, true},
        {"max_pullback_bars",    1.0,  1.0,  6.0,  true},
        {"sl_atr_mult",          1.0,  0.0,  8.0,  false},
        {"exit_ema",             20.0, 0.0,  200.0, true},
        {"atr_period",           3.0,  7.0,  30.0, true},
        // widened levers (HeikinAshiStrategy filters/exits)
        {"adx_period",           3.0,  7.0,  30.0, true},
        {"rsi_period",           2.0,  2.0,  14.0, true},
        {"trend_ema",            50.0, 0.0,  200.0, true},
        {"confirm_bars",         1.0,  1.0,  4.0,  true},
        {"vol_mult",             0.2,  1.0,  2.5,  false},
        {"vol_period",           5.0,  10.0, 40.0, true},
        {"bb_min_width",         1.5,  2.0,  15.0, false},
        {"exit_bearish_bars",    1.0,  0.0,  4.0,  true},
        {"ema_slope_bars",       1.0,  0.0,  8.0,  true},
        {"max_consec_losses",    1.0,  0.0,  6.0,  true},
        // v2 breakout / momentum levers
        {"donchian_period",      3.0,  10.0, 60.0, true},
        {"donchian_exit_period", 2.0,  5.0,  40.0, true},
        {"adx_slope",            1.0,  1.0,  10.0, true},
        {"roc_min",              1.0,  0.0,  20.0, false},
        {"roc_period",           5.0,  5.0,  60.0, true},
        {"kc_mult",              0.25, 0.5,  4.0,  false},
        {"kc_ema",               5.0,  10.0, 40.0, true},
    };

    // Risk/filter params carried over when swapping entry_mode families.
    private static final String[] CARRY = {
        "adx_filter", "adx_threshold", "adx_period", "trailing_stop", "trail_atr_mult",
        "trail_activation_pct", "tp_atr_mult", "sl_atr_mult", "atr_period", "trend_ema",
        "exit_ema", "cooldown_bars", "ha_flip_exit", "confirm_bars", "strong_candle",
        "volume_filter", "vol_mult", "vol_period", "bb_filter", "bb_min_width",
        "exit_bearish_bars", "ema_slope_bars", "max_consec_losses",
        "adx_rising", "adx_slope", "roc_min", "roc_period",
        "donchian_exit", "donchian_exit_period",
    };

    private static final java.util.Random RNG = new java.util.Random(System.nanoTime());

    static final class SectorState {
        String sector;
        String origName;
        double origComposite;
        String cls;
        Map<String, Object> best;
        double bestComposite;
        double scale = 1.0;
        boolean converged = false;
        boolean improved = false;   // improved vs origComposite at any point
    }

    public static void main(String[] args) throws Exception {
        Market market = Market.current();
        LinkedHashMap<String, Supplier<Strategy>> registry = VariantRegistry.buildAll();
        DataFetcher fetcher = DataFetcher.createDefault();
        ObjectMapper om = new ObjectMapper();

        Path winnersCsv = market.winnersCsv();
        if (!Files.exists(winnersCsv)) {
            System.err.println("No winners CSV at " + winnersCsv + " — run SectorAllPhasesOptimizer first.");
            return;
        }

        // Read winners CSV (preserve header + row order for rewrite).
        List<String> csvLines = Files.readAllLines(winnersCsv);
        String header = csvLines.get(0);
        LinkedHashMap<String, String> rawRowBySector = new LinkedHashMap<>();
        LinkedHashMap<String, Double> compBySector = new LinkedHashMap<>();
        LinkedHashMap<String, String> nameBySector = new LinkedHashMap<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String line = csvLines.get(i);
            if (line.isBlank()) continue;
            String[] c = line.split(",");
            rawRowBySector.put(c[0], line);
            nameBySector.put(c[0], c[1]);
            compBySector.put(c[0], Double.parseDouble(c[2]));
        }

        // Existing refined variants (persisted state from prior runs).
        Map<String, Map<String, Object>> refinedRaw = new LinkedHashMap<>();
        Path refinedPath = RefinedVariants.path(market);
        if (Files.exists(refinedPath)) {
            refinedRaw = om.readValue(refinedPath.toFile(),
                new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
        }

        System.out.printf("%n=== SECTOR REFINE OPTIMIZER [market=%s] — %d sectors ===%n",
            market.code(), rawRowBySector.size());

        List<SectorState> states = new ArrayList<>();
        Map<String, Map<String, List<Bar>>> barsCache = new HashMap<>();

        for (String sector : rawRowBySector.keySet()) {
            String name = nameBySector.get(sector);
            Map<String, Object> seed = seedParams(name, refinedRaw);
            String cls = seedClass(name, refinedRaw);
            if (seed == null) {
                System.out.printf("  [%s] no seed params for '%s' — skipping%n", sector, name);
                continue;
            }
            // Load bars once per sector.
            Map<String, List<Bar>> bars = loadBars(market, sector, fetcher);
            if (bars.size() < 3) {
                System.out.printf("  [%s] too few stocks with data (%d) — skipping%n", sector, bars.size());
                continue;
            }
            barsCache.put(sector, bars);

            SectorState st = new SectorState();
            st.sector = sector;
            st.origName = name;
            st.cls = cls;
            st.best = new HashMap<>(seed);
            VariantResult base = SectorAllPhasesOptimizer.evaluateFast(sector + "#seed",
                VariantRegistry.build(cls, withName(seed, sector + "#seed")), bars);
            st.bestComposite = base != null ? base.composite : compBySector.get(sector);
            st.origComposite = st.bestComposite;

            // Give the new breakout families (Donchian/Keltner) a fair, non-greedy
            // shot from scratch — the greedy neighbourhood can't reach a different
            // family's optimum from a flip/regime champion.
            List<Map<String, Object>> fam = familyExplore();
            Object[] famBest = fam.parallelStream()
                .map(p -> {
                    VariantResult vr = SectorAllPhasesOptimizer.evaluateFast(sector + "#f",
                        VariantRegistry.build("HeikinAshiStrategy", withName(p, sector + "#f")), bars);
                    return new Object[]{ vr != null ? vr.composite : Double.NEGATIVE_INFINITY, p };
                })
                .max(Comparator.comparingDouble(x -> (Double) x[0])).orElse(null);
            if (famBest != null && (Double) famBest[0] > st.bestComposite + EPS) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fm = (Map<String, Object>) famBest[1];
                st.best = new HashMap<>(fm);
                st.bestComposite = (Double) famBest[0];
                st.cls = "HeikinAshiStrategy";
                st.improved = true;
                System.out.printf("  [%s] family-explore ADOPTED %s champion comp=%.4f (was %.4f)%n",
                    sector, fm.get("entry_mode"), st.bestComposite, st.origComposite);
            }

            states.add(st);
            System.out.printf("  [%s] seed=%s comp=%.4f (%s, %d stocks)%n",
                sector, name, st.bestComposite, st.cls, bars.size());
        }

        // ── Diagnostic: print champion vs each new mechanic, then exit ──
        if (Arrays.asList(args).contains("--diag")) {
            for (SectorState st : states) {
                Map<String, List<Bar>> bars = barsCache.get(st.sector);
                System.out.printf("  [%s] champion comp=%.4f (mode=%s)%n",
                    st.sector, st.bestComposite, st.best.getOrDefault("entry_mode", "flip"));
                Map<String, Map<String, Object>> tests = new LinkedHashMap<>();
                Map<String, Object> a = new HashMap<>(st.best); a.put("donchian_exit", true); a.put("donchian_exit_period", 10); tests.put("+donchExit", a);
                Map<String, Object> b = new HashMap<>(st.best); b.put("adx_rising", true); b.put("adx_slope", 3); tests.put("+adxRising", b);
                Map<String, Object> c = new HashMap<>(st.best); c.put("roc_min", 3.0); c.put("roc_period", 20); tests.put("+rocMin3", c);
                tests.put("mode=donchian", modeSwap(st.best, "donchian"));
                tests.put("mode=keltner", modeSwap(st.best, "keltner"));
                for (Map.Entry<String, Map<String, Object>> e : tests.entrySet()) {
                    VariantResult vr = SectorAllPhasesOptimizer.evaluateFast(st.sector + "#d",
                        VariantRegistry.build(st.cls, withName(e.getValue(), st.sector + "#d")), bars);
                    System.out.printf("      %-14s comp=%-8s stocks=%s%n", e.getKey(),
                        vr != null ? String.format(Locale.US, "%.4f", vr.composite) : "null",
                        vr != null ? vr.stocksUsed : "-");
                }
            }
            return;
        }

        // ── Pattern-search loop ──────────────────────────────────────
        int round = 0;
        while (round < MAX_ROUNDS) {
            round++;
            int improvedThisRound = 0;
            for (SectorState st : states) {
                if (st.converged) continue;
                Map<String, List<Bar>> bars = barsCache.get(st.sector);

                List<Map<String, Object>> neighbors = neighbors(st.best, st.scale);
                // Evaluate neighbors in parallel; keep the best-scoring candidate + its params.
                Object[] best = neighbors.parallelStream()
                    .map(p -> {
                        VariantResult vr = SectorAllPhasesOptimizer.evaluateFast(
                            st.sector + "#c", VariantRegistry.build(st.cls, withName(p, st.sector + "#c")), bars);
                        return new Object[]{ vr != null ? vr.composite : Double.NEGATIVE_INFINITY, p };
                    })
                    .max(Comparator.comparingDouble(a -> (Double) a[0]))
                    .orElse(null);
                double bestComp = best != null ? (Double) best[0] : Double.NEGATIVE_INFINITY;
                @SuppressWarnings("unchecked")
                Map<String, Object> bestMap = best != null ? (Map<String, Object>) best[1] : null;

                if (bestMap != null && bestComp > st.bestComposite + EPS) {
                    st.best = new HashMap<>(bestMap);
                    st.bestComposite = bestComp;
                    st.improved = true;
                    improvedThisRound++;
                    System.out.printf("    [r%d][%s] ↑ comp %.4f  (scale %.3f)  %s%n",
                        round, st.sector, bestComp, st.scale, deltaDesc(st));
                } else {
                    st.scale *= 0.5;
                    if (st.scale < MIN_SCALE) st.converged = true;
                }
            }
            long active = states.stream().filter(s -> !s.converged).count();
            System.out.printf("  round %d: improved=%d, active=%d/%d%n",
                round, improvedThisRound, active, states.size());
            if (active == 0) break;
        }

        // ── Promote improvements ─────────────────────────────────────
        int promoted = 0;
        Map<String, Map<String, Object>> refinedOut = new LinkedHashMap<>(refinedRaw);
        for (SectorState st : states) {
            // Keep any previously-refined champion for this sector's current name.
            if (!st.improved || st.bestComposite <= st.origComposite + EPS) continue;
            String refName = "RF:" + st.sector;
            Map<String, Object> best = new HashMap<>(st.best);
            best.put("variant_name", refName);

            // Full evaluate() for DSR / portfolio-Sharpe on the final champion.
            Map<String, List<Bar>> bars = barsCache.get(st.sector);
            VariantResult full = SectorAllPhasesOptimizer.evaluate(
                refName, VariantRegistry.build(st.cls, best), bars);
            if (full == null) continue;

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("class", st.cls);
            spec.put("params", best);
            refinedOut.put(refName, spec);

            rawRowBySector.put(st.sector, formatRow(st.sector, refName, full));
            promoted++;
            System.out.printf("  PROMOTE [%s] %.4f → %.4f  (%s)%n",
                st.sector, st.origComposite, full.composite, refName);
        }

        // ── Persist ──────────────────────────────────────────────────
        if (promoted > 0) {
            Files.createDirectories(market.phasesDir());
            // backup + rewrite winners csv
            Files.copy(winnersCsv, market.phasesDir().resolve("new_winners.csv.bak"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (BufferedWriter w = Files.newBufferedWriter(winnersCsv)) {
                w.write(header); w.newLine();
                for (String row : rawRowBySector.values()) { w.write(row); w.newLine(); }
            }
            om.writerWithDefaultPrettyPrinter().writeValue(refinedPath.toFile(), refinedOut);
            System.out.printf("%n  Wrote %d refined winner(s) → %s%n", promoted, refinedPath);
        }

        System.out.printf("%n=== DONE: %d/%d sectors improved after %d rounds ===%n",
            promoted, states.size(), round);
        System.out.println(promoted == 0
            ? "  No sector improved — winners are at a local optimum (converged)."
            : "  Re-run to continue searching around the new champions.");
    }

    // ── Seed helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> seedParams(String name, Map<String, Map<String, Object>> refinedRaw) {
        if (name.startsWith("RF:") && refinedRaw.containsKey(name)) {
            Object p = refinedRaw.get(name).get("params");
            if (p instanceof Map) return new HashMap<>((Map<String, Object>) p);
        }
        Map<String, Object> p = VariantRegistry.VARIANT_PARAMS.get(name);
        return p != null ? new HashMap<>(p) : null;
    }

    private static String seedClass(String name, Map<String, Map<String, Object>> refinedRaw) {
        if (name.startsWith("RF:") && refinedRaw.containsKey(name)) {
            return String.valueOf(refinedRaw.get(name).getOrDefault("class", "HeikinAshiStrategy"));
        }
        return VariantRegistry.VARIANT_CLASS.getOrDefault(name, "HeikinAshiStrategy");
    }

    private static Map<String, Object> withName(Map<String, Object> p, String name) {
        Map<String, Object> m = new HashMap<>(p);
        m.put("variant_name", name);
        return m;
    }

    private static Map<String, List<Bar>> loadBars(Market market, String sector, DataFetcher fetcher) throws Exception {
        Map<String, List<Bar>> out = new LinkedHashMap<>();
        Path f = market.sectorsFsDir().resolve(sector + ".txt");
        if (!Files.exists(f)) return out;
        for (String stock : Files.readAllLines(f)) {
            stock = stock.trim();
            if (stock.isEmpty()) continue;
            List<Bar> bars = fetcher.fetchData(stock, BacktestConfig.WARMUP_START);
            if (bars != null && bars.size() >= MIN_BARS) out.put(stock, bars);
        }
        return out;
    }

    // ── Neighbor generation (coordinate search + structural toggles) ──

    private static List<Map<String, Object>> neighbors(Map<String, Object> cur, double scale) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Perturb each tunable that is present in the current champion.
        for (Object[] t : TUNABLES) {
            String key = (String) t[0];
            if (!cur.containsKey(key)) continue;
            double baseStep = (double) t[1], lo = (double) t[2], hi = (double) t[3];
            boolean isInt = (boolean) t[4];
            double curV = ((Number) cur.get(key)).doubleValue();
            double step = baseStep * scale;
            if (isInt) step = Math.max(1, Math.round(step));
            for (double dir : new double[]{+1, -1}) {
                double nv = clamp(curV + dir * step, lo, hi);
                if (isInt) nv = Math.round(nv);
                if (nv == curV) continue;
                Map<String, Object> m = new HashMap<>(cur);
                m.put(key, isInt ? (Object) (int) nv : (Object) nv);
                addUnique(out, seen, m);
            }
        }
        // 2. Structural toggles — add/remove filters & exits (new mechanics).
        double tp = num(cur, "tp_atr_mult", 0);
        put(out, seen, cur, "tp_atr_mult", tp > 0 ? 0.0 : 15.0);
        put(out, seen, cur, "strong_candle", !bool(cur, "strong_candle"));
        if (!bool(cur, "volume_filter")) putAll(out, seen, cur, "volume_filter", true, "vol_mult", 1.2, "vol_period", 20);
        else put(out, seen, cur, "volume_filter", false);
        if (!bool(cur, "bb_filter")) putAll(out, seen, cur, "bb_filter", true, "bb_min_width", 6.0);
        else put(out, seen, cur, "bb_filter", false);
        put(out, seen, cur, "ha_flip_exit", !bool(cur, "ha_flip_exit"));
        if (num(cur, "confirm_bars", 1) < 2) put(out, seen, cur, "confirm_bars", 2);
        if (num(cur, "exit_bearish_bars", 0) == 0) put(out, seen, cur, "exit_bearish_bars", 2);
        if (num(cur, "ema_slope_bars", 0) == 0) put(out, seen, cur, "ema_slope_bars", 3);
        if (num(cur, "max_consec_losses", 0) == 0) put(out, seen, cur, "max_consec_losses", 3);
        if (num(cur, "exit_ema", 0) == 0) put(out, seen, cur, "exit_ema", 50);
        if (num(cur, "trend_ema", 0) == 0) put(out, seen, cur, "trend_ema", 200);
        if (num(cur, "sl_atr_mult", 0) == 0) put(out, seen, cur, "sl_atr_mult", 3.0);
        // v2: momentum / breakout filters & exits
        put(out, seen, cur, "adx_rising", !bool(cur, "adx_rising"));  // adx_slope carried/default
        if (!cur.containsKey("adx_slope")) putAll(out, seen, cur, "adx_rising", true, "adx_slope", 3);
        if (num(cur, "roc_min", 0) == 0) putAll(out, seen, cur, "roc_min", 3.0, "roc_period", 20);
        else put(out, seen, cur, "roc_min", 0.0);
        if (!bool(cur, "donchian_exit")) putAll(out, seen, cur, "donchian_exit", true, "donchian_exit_period", 10);
        else put(out, seen, cur, "donchian_exit", false);

        // 3. Entry-mode swaps — migrate strategy family, keep risk management.
        String mode = String.valueOf(cur.getOrDefault("entry_mode", "flip"));
        for (String alt : new String[]{"flip", "rsi_pullback", "pullback", "regime", "donchian", "keltner"}) {
            if (!alt.equals(mode)) addUnique(out, seen, modeSwap(cur, alt));
        }

        // 4. Joint random jitter — perturb 2–4 present tunables simultaneously.
        List<Object[]> present = new ArrayList<>();
        for (Object[] t : TUNABLES) if (cur.containsKey((String) t[0])) present.add(t);
        for (int j = 0; j < 10 && present.size() >= 2; j++) {
            Map<String, Object> m = new HashMap<>(cur);
            int k = 2 + RNG.nextInt(3);
            Collections.shuffle(present, RNG);
            for (int x = 0; x < Math.min(k, present.size()); x++) {
                Object[] t = present.get(x);
                String key = (String) t[0];
                double baseStep = (double) t[1], lo = (double) t[2], hi = (double) t[3];
                boolean isInt = (boolean) t[4];
                double curV = ((Number) cur.get(key)).doubleValue();
                double step = baseStep * scale * (0.5 + RNG.nextDouble());
                double dir = RNG.nextBoolean() ? 1 : -1;
                double nv = clamp(curV + dir * step, lo, hi);
                if (isInt) nv = Math.round(nv);
                m.put(key, isInt ? (Object) (int) nv : (Object) nv);
            }
            addUnique(out, seen, m);
        }
        return out;
    }

    private static void put(List<Map<String, Object>> out, Set<String> seen,
                            Map<String, Object> cur, String k, Object v) {
        Map<String, Object> m = new HashMap<>(cur);
        m.put(k, v);
        addUnique(out, seen, m);
    }

    private static void putAll(List<Map<String, Object>> out, Set<String> seen,
                               Map<String, Object> cur, Object... kv) {
        Map<String, Object> m = new HashMap<>(cur);
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        addUnique(out, seen, m);
    }

    private static Map<String, Object> modeSwap(Map<String, Object> cur, String mode) {
        Map<String, Object> m = new HashMap<>();
        for (String k : CARRY) if (cur.containsKey(k)) m.put(k, cur.get(k));
        m.put("entry_mode", mode);
        m.putIfAbsent("trailing_stop", true);
        switch (mode) {
            case "rsi_pullback":
                m.putIfAbsent("rsi_period", 2);
                m.put("rsi_entry", num(cur, "rsi_entry", 55.0));
                break;
            case "pullback":
                m.put("min_trend_bars", (int) num(cur, "min_trend_bars", 5));
                m.put("max_pullback_bars", (int) num(cur, "max_pullback_bars", 2));
                break;
            case "regime":
                m.put("regime_window", (int) num(cur, "regime_window", 10));
                m.put("regime_threshold", num(cur, "regime_threshold", 0.6));
                m.putIfAbsent("rsi_period", 2);
                m.put("rsi_entry", num(cur, "rsi_entry", 55.0));
                break;
            case "donchian":
                m.put("donchian_period", (int) num(cur, "donchian_period", 20));
                break;
            case "keltner":
                m.put("kc_ema", (int) num(cur, "kc_ema", 20));
                m.put("kc_mult", num(cur, "kc_mult", 1.5));
                break;
            default: break; // flip — no mode-specific params
        }
        return m;
    }

    /** From-scratch coarse grid over the Donchian & Keltner breakout families. */
    private static List<Map<String, Object>> familyExplore() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double[] trails = {3.0, 4.0, 5.0};
        double[] tps = {0.0, 20.0};
        double[] acts = {0.0, 20.0};
        double[] adxs = {0.0, 25.0};     // 0 = no ADX filter
        int[] cds = {0, 30};             // cooldown after loss
        for (int dp : new int[]{20, 30, 40, 55}) {
            for (double tr : trails) for (double tp : tps) for (double act : acts)
            for (double adx : adxs) for (int cd : cds) {
                Map<String, Object> m = base("donchian");
                m.put("donchian_period", dp);
                m.put("trail_atr_mult", tr); m.put("trail_activation_pct", act);
                m.put("tp_atr_mult", tp);
                if (adx > 0) { m.put("adx_filter", true); m.put("adx_threshold", adx); }
                if (cd > 0) m.put("cooldown_bars", cd);
                addUnique(out, seen, m);
            }
        }
        for (int ke : new int[]{15, 20, 30}) {
            for (double km : new double[]{1.0, 1.5, 2.0}) for (double tr : trails)
            for (double tp : tps) for (double adx : adxs) for (int cd : cds) {
                Map<String, Object> m = base("keltner");
                m.put("kc_ema", ke); m.put("kc_mult", km);
                m.put("trail_atr_mult", tr); m.put("trail_activation_pct", 0.0);
                m.put("tp_atr_mult", tp);
                if (adx > 0) { m.put("adx_filter", true); m.put("adx_threshold", adx); }
                if (cd > 0) m.put("cooldown_bars", cd);
                addUnique(out, seen, m);
            }
        }
        return out;
    }

    private static Map<String, Object> base(String mode) {
        Map<String, Object> m = new HashMap<>();
        m.put("entry_mode", mode);
        m.put("trailing_stop", true);
        m.put("atr_period", 14);
        return m;
    }

    private static void addUnique(List<Map<String, Object>> out, Set<String> seen, Map<String, Object> m) {
        String sig = sig(m);
        if (seen.add(sig)) out.add(m);
    }

    private static String sig(Map<String, Object> m) {
        TreeMap<String, Object> t = new TreeMap<>(m);
        t.remove("variant_name");
        return t.toString();
    }

    private static double num(Map<String, Object> m, String k, double def) {
        Object v = m.get(k); return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
    private static boolean bool(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof Boolean && (Boolean) v;
    }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String deltaDesc(SectorState st) {
        // Compact description of the champion's key params.
        StringBuilder sb = new StringBuilder();
        for (String k : new String[]{"entry_mode", "adx_threshold", "trail_atr_mult",
                "trail_activation_pct", "tp_atr_mult", "rsi_entry", "cooldown_bars",
                "regime_threshold", "st_flip_exit", "exit_ema", "trend_ema"}) {
            if (st.best.containsKey(k)) sb.append(k).append('=').append(st.best.get(k)).append(' ');
        }
        return sb.toString().trim();
    }

    private static String formatRow(String sector, String name, VariantResult v) {
        return String.format(Locale.US, "%s,%s,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.4f,%d",
            sector, name, v.composite,
            v.isFull.getAvgCagr(), v.isEarly.getAvgCagr(),
            v.oosHist.getAvgCagr(), v.oosRecent.getAvgCagr(),
            v.minCagr, v.maxMdd, v.minPf, v.minWr,
            v.portSharpe, v.dsrVest, v.stocksUsed);
    }
}
