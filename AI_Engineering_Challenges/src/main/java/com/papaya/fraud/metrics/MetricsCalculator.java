package com.papaya.fraud.metrics;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.Ranker;
import com.papaya.fraud.engine.ScoreCalculator;
import com.papaya.fraud.io.Json;
import com.papaya.fraud.metrics.MetricsResult.GroupRecall;
import com.papaya.fraud.metrics.MetricsResult.LegitScenario;
import com.papaya.fraud.metrics.MetricsResult.PatternRecall;
import com.papaya.fraud.metrics.MetricsResult.PrecisionAtK;
import com.papaya.fraud.metrics.MetricsResult.RulePrecision;
import com.papaya.fraud.metrics.ThresholdSelector.SweepRow;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Computes {@link MetricsResult} from the engine output and the ground truth. */
public final class MetricsCalculator {

    /** L10: patterns whose rule matches a reference table or a fixed key shared with the generator. */
    public static final List<String> REFERENCE_TABLE_PATTERNS =
            List.of("duplicate_claim", "unbundling", "diagnosis_procedure_mismatch");
    public static final List<String> STATISTICAL_PATTERNS =
            List.of("rapid_resubmission", "upcoding", "phantom_billing", "weekend_anomaly", "amount_clustering");
    public static final List<Integer> PRECISION_AT = List.of(50, 100, 200);
    public static final int MAX_THRESHOLD = 100;

    private MetricsCalculator() {
    }

    /** Confusion matrix at T: predicted fraud when risk_score &gt;= T. */
    static ConfusionMatrix confusion(List<ScoredClaim> scored, Map<String, GroundTruthLabel> labels, int threshold) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (ScoredClaim s : scored) {
            boolean fraud = label(labels, s).fraud();
            boolean predicted = s.riskScore() >= threshold;
            if (fraud && predicted) {
                tp++;
            } else if (fraud) {
                fn++;
            } else if (predicted) {
                fp++;
            } else {
                tn++;
            }
        }
        return new ConfusionMatrix(tp, fp, fn, tn);
    }

    static List<SweepRow> sweep(List<ScoredClaim> scored, Map<String, GroundTruthLabel> labels) {
        List<SweepRow> rows = new ArrayList<>();
        for (int t = 1; t <= MAX_THRESHOLD; t++) {
            rows.add(new SweepRow(t, confusion(scored, labels, t)));
        }
        return rows;
    }

    static MetricsResult compute(List<ScoredClaim> ranked, Map<String, GroundTruthLabel> labels, FraudConfig config,
                                 int threshold, Map<RuleId, Record> coverage) {
        if (ranked.size() != labels.size()) {
            throw new IllegalArgumentException(ranked.size() + " scored claims but " + labels.size()
                    + " ground truth rows");
        }
        List<PatternRecall> patterns = new ArrayList<>();
        for (RuleId rule : RuleId.values()) {
            String pattern = rule.id();
            int claims = 0;
            int caught = 0;
            int own = 0;
            for (ScoredClaim s : ranked) {
                if (!label(labels, s).pattern().equals(pattern)) {
                    continue;
                }
                claims++;
                if (s.riskScore() >= threshold) {
                    caught++;
                }
                if (s.flags().stream().anyMatch(f -> f.rule() == rule)) {
                    own++;
                }
            }
            patterns.add(new PatternRecall(pattern, claims, caught, own));
        }

        List<GroupRecall> groups = List.of(
                group("reference_table", REFERENCE_TABLE_PATTERNS, patterns),
                group("statistical", STATISTICAL_PATTERNS, patterns));

        List<RulePrecision> rules = new ArrayList<>();
        for (RuleId rule : RuleId.values()) {
            int flagged = 0;
            int fraudAny = 0;
            int fraudSame = 0;
            for (ScoredClaim s : ranked) {
                if (s.flags().stream().noneMatch(f -> f.rule() == rule)) {
                    continue;
                }
                flagged++;
                GroundTruthLabel l = label(labels, s);
                if (l.fraud()) {
                    fraudAny++;
                    if (l.pattern().equals(rule.id())) {
                        fraudSame++;
                    }
                }
            }
            rules.add(new RulePrecision(rule.id(), flagged, fraudAny, fraudSame));
        }

        Map<String, LegitScenario> legit = new TreeMap<>();
        Map<String, int[]> legitCounts = new TreeMap<>();
        Map<String, Map<String, Integer>> legitFlags = new TreeMap<>();
        Map<String, String[]> legitKeys = new TreeMap<>();
        for (ScoredClaim s : ranked) {
            GroundTruthLabel l = label(labels, s);
            if (l.fraud()) {
                continue;
            }
            String key = l.pattern() + "/" + l.scenario();
            legitKeys.putIfAbsent(key, new String[] {l.pattern(), l.scenario()});
            int[] c = legitCounts.computeIfAbsent(key, k -> new int[2]);
            c[0]++;
            if (s.riskScore() >= threshold) {
                c[1]++;
            }
            Map<String, Integer> byRule = legitFlags.computeIfAbsent(key, k -> new TreeMap<>());
            for (Flag f : s.flags()) {
                byRule.merge(f.rule().id(), 1, Integer::sum);
            }
        }
        legitCounts.forEach((key, c) -> legit.put(key, new LegitScenario(legitKeys.get(key)[0], legitKeys.get(key)[1],
                c[0], c[1], legitFlags.get(key))));

        List<ScoredClaim> equalRanking = equalWeightRanking(ranked, config);
        List<PrecisionAtK> ranking = new ArrayList<>();
        for (int k : PRECISION_AT) {
            int n = Math.min(k, ranked.size());
            ranking.add(new PrecisionAtK(n, fraudInTop(ranked, labels, n), fraudInTop(equalRanking, labels, n)));
        }

        Map<String, Map<String, Object>> coverageView = new LinkedHashMap<>();
        coverage.forEach((rule, record) -> coverageView.put(rule.id(), toMap(record)));

        return new MetricsResult(threshold, confusion(ranked, labels, threshold), groups, patterns, rules,
                List.copyOf(legit.values()), ranking, coverageView, sweep(ranked, labels));
    }

    /** The same flags re-scored with every weight set to 1, re-ranked with the engine's tie-breaks (S5). */
    static List<ScoredClaim> equalWeightRanking(List<ScoredClaim> ranked, FraudConfig config) {
        ScoreCalculator equal = new ScoreCalculator(config.withEqualWeights());
        return Ranker.rank(ranked.stream()
                .map(s -> new ScoredClaim(s.claim(), equal.score(s.flags()), s.flags())).toList());
    }

    private static int fraudInTop(List<ScoredClaim> ranking, Map<String, GroundTruthLabel> labels, int k) {
        return (int) ranking.stream().limit(k).filter(s -> label(labels, s).fraud()).count();
    }

    private static GroupRecall group(String name, List<String> members, List<PatternRecall> patterns) {
        Set<String> set = Set.copyOf(members);
        List<PatternRecall> in = patterns.stream().filter(p -> set.contains(p.pattern())).toList();
        return new GroupRecall(name, members, in.stream().mapToInt(PatternRecall::claims).sum(),
                in.stream().mapToInt(PatternRecall::caught).sum());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Record record) {
        return Json.mapper().convertValue(record, LinkedHashMap.class);
    }

    private static GroundTruthLabel label(Map<String, GroundTruthLabel> labels, ScoredClaim s) {
        GroundTruthLabel l = labels.get(s.claim().claimId());
        if (l == null) {
            throw new IllegalArgumentException("no ground truth for " + s.claim().claimId());
        }
        return l;
    }
}
