package com.stock.analyzer.backtest.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.analyzer.backtest.strategy.Strategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loads per-market "refined" strategy variants discovered by
 * {@code SectorRefineOptimizer} (a local/pattern search that beats the registry
 * winners). Stored as JSON at {@code <phasesDir>/refined_variants.json}:
 *
 * <pre>{ "RF:industrials": { "class": "HeikinAshiStrategy", "params": { ... } }, ... }</pre>
 *
 * These are merged into the strategy map used by SectorDetailReport and
 * LiveSignalScanner so that a refined winner named in {@code new_winners.csv}
 * resolves to a runnable strategy.
 */
public final class RefinedVariants {

    private RefinedVariants() {}

    public static final String FILE_NAME = "refined_variants.json";

    public static Path path(Market market) {
        return market.phasesDir().resolve(FILE_NAME);
    }

    public static Map<String, Supplier<Strategy>> load(Market market) {
        Map<String, Supplier<Strategy>> out = new LinkedHashMap<>();
        Path f = path(market);
        if (!Files.exists(f)) return out;
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Map<String, Object>> raw = om.readValue(
                f.toFile(), new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
            for (Map.Entry<String, Map<String, Object>> e : raw.entrySet()) {
                String name = e.getKey();
                Map<String, Object> spec = e.getValue();
                if (spec == null) continue;
                String cls = String.valueOf(spec.getOrDefault("class", "HeikinAshiStrategy"));
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) spec.get("params");
                if (params == null) continue;
                Map<String, Object> p = new HashMap<>(params);
                p.put("variant_name", name);
                out.put(name, VariantRegistry.build(cls, p));
            }
        } catch (Exception ex) {
            System.err.println("  ⚠ RefinedVariants.load(" + market.code() + "): " + ex.getMessage());
        }
        return out;
    }
}
