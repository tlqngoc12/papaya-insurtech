package com.papaya.fraud.config;

import com.papaya.fraud.model.RuleId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Validated engine configuration: one weight (1-5) per rule, rule parameters, and the classification threshold T.
 * T is absent from the default {@code fraud-config.json}; it only exists in {@code config/frozen-config.json},
 * written by {@code tune} on the tuning dataset (S3, Q10).
 */
public final class FraudConfig {

    private final Map<RuleId, Integer> weights;
    private final RuleParams params;
    private final OptionalInt threshold;

    FraudConfig(Map<RuleId, Integer> weights, RuleParams params, OptionalInt threshold) {
        this.weights = Collections.unmodifiableMap(new EnumMap<>(weights));
        this.params = params;
        this.threshold = threshold;
    }

    public int weight(RuleId rule) {
        return weights.get(rule);
    }

    public Map<RuleId, Integer> weights() {
        return weights;
    }

    /** Sum of all weights: the S1-A denominator (28 with the weights of section G). */
    public int totalWeight() {
        return weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    public RuleParams params() {
        return params;
    }

    public OptionalInt threshold() {
        return threshold;
    }

    /**
     * T for commands that classify claims. Fails instead of falling back to a default, so metrics are never
     * computed with a threshold that was not chosen on the tuning dataset.
     */
    public int requireThreshold() {
        if (threshold.isEmpty()) {
            throw new ConfigException("No threshold T in this config. T is chosen by 'tune' on the tuning dataset"
                    + " and stored in config/frozen-config.json; pass it with --config config/frozen-config.json.");
        }
        return threshold.getAsInt();
    }
}
