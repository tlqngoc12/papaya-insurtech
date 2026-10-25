package com.papaya.fraud.metrics;

import com.papaya.fraud.metrics.ThresholdSelector.SweepRow;
import java.util.List;
import java.util.Map;

/**
 * Everything the metrics report shows, computed by {@link MetricsCalculator}; nothing in it is written by hand.
 */
public record MetricsResult(int threshold, ConfusionMatrix confusion, List<GroupRecall> groups,
                            List<PatternRecall> patterns, List<RulePrecision> rules, List<LegitScenario> legit,
                            List<PrecisionAtK> ranking, Map<String, Map<String, Object>> coverage,
                            List<SweepRow> sweep) {

    /** Recall of a group of patterns (L10: reference-table rules vs statistical rules). */
    public record GroupRecall(String group, List<String> patterns, int claims, int caught) {
        public double recall() {
            return claims == 0 ? Double.NaN : (double) caught / claims;
        }
    }

    /**
     * Per fraud pattern: claims of that pattern, how many score at least T (caught), and how many carry a flag of the
     * rule of the same name (whatever their score).
     */
    public record PatternRecall(String pattern, int claims, int caught, int flaggedByOwnRule) {
        public double recall() {
            return claims == 0 ? Double.NaN : (double) caught / claims;
        }
    }

    /**
     * Per rule, over the claims carrying a flag of that rule (whatever their score): how many are fraud of any pattern,
     * and how many are fraud of the pattern of the same name.
     */
    public record RulePrecision(String rule, int flagged, int fraudAnyPattern, int fraudSamePattern) {
        public double precisionAny() {
            return flagged == 0 ? Double.NaN : (double) fraudAnyPattern / flagged;
        }

        public double precisionSamePattern() {
            return flagged == 0 ? Double.NaN : (double) fraudSamePattern / flagged;
        }
    }

    /**
     * Legit claims of one label and scenario: how many score at least T, and the flags they carry (counts per rule,
     * whatever the score).
     */
    public record LegitScenario(String label, String scenario, int claims, int flaggedAtThreshold,
                                Map<String, Integer> flagsByRule) {
    }

    /** Fraud claims among the top k of the engine ranking and of the ranking with all weights set to 1. */
    public record PrecisionAtK(int k, int fraudEngine, int fraudEqualWeights) {
        public double engine() {
            return k == 0 ? Double.NaN : (double) fraudEngine / k;
        }

        public double equalWeights() {
            return k == 0 ? Double.NaN : (double) fraudEqualWeights / k;
        }
    }
}
