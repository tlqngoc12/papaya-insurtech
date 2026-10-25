package com.papaya.fraud.metrics;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.Ranker;
import com.papaya.fraud.metrics.MetricsResult.GroupRecall;
import com.papaya.fraud.metrics.MetricsResult.LegitScenario;
import com.papaya.fraud.metrics.MetricsResult.PatternRecall;
import com.papaya.fraud.metrics.MetricsResult.PrecisionAtK;
import com.papaya.fraud.metrics.MetricsResult.RulePrecision;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Eight hand-built scored claims with known labels:
 *
 * <pre>
 *   A fraud duplicate   [duplicate]   18
 *   B fraud upcoding    [upcoding]    14
 *   C fraud upcoding    []             0   (missed)
 *   D fraud weekend     [weekend]     11
 *   E legit none/base   [rapid]        7
 *   F legit noise/band  [clustering]   7
 *   G legit none/base   []             0
 *   H legit near/day30  []             0
 * </pre>
 */
class MetricsCalculatorTest {

    private final FraudConfig config = ConfigLoader.loadDefault();
    private final Map<String, GroundTruthLabel> labels = new LinkedHashMap<>();
    private final List<ScoredClaim> ranked;

    MetricsCalculatorTest() {
        List<ScoredClaim> scored = new ArrayList<>();
        add(scored, "A", 18, 900, true, "duplicate_claim", "duplicate", RuleId.DUPLICATE_CLAIM);
        add(scored, "B", 14, 800, true, "upcoding", "upcode_strong", RuleId.UPCODING);
        add(scored, "C", 0, 700, true, "upcoding", "upcode_mild");
        add(scored, "D", 11, 600, true, "weekend_anomaly", "weekend_surgery", RuleId.WEEKEND_ANOMALY);
        add(scored, "E", 7, 500, false, "none", "base", RuleId.RAPID_RESUBMISSION);
        add(scored, "F", 7, 400, false, "legit_noise", "band_noise", RuleId.AMOUNT_CLUSTERING);
        add(scored, "G", 0, 300, false, "none", "base");
        add(scored, "H", 0, 200, false, "legit_near_miss", "day_30_claims");
        ranked = Ranker.rank(scored);
    }

    private void add(List<ScoredClaim> scored, String id, int score, long amount, boolean fraud, String pattern,
                     String scenario, RuleId... rules) {
        List<Flag> flags = new ArrayList<>();
        for (RuleId r : rules) {
            flags.add(new Flag(r, config.weight(r), "test"));
        }
        scored.add(new ScoredClaim(claim(id).withMember("M-" + id).withAmount(amount).build(), score, flags));
        labels.put(id, new GroundTruthLabel(id, fraud, pattern, "", scenario));
    }

    private MetricsResult at(int threshold) {
        return MetricsCalculator.compute(ranked, labels, config, threshold, Map.of());
    }

    @Test
    void confusionMatrixFollowsTheThreshold() {
        assertEquals(new ConfusionMatrix(3, 0, 1, 4), at(8).confusion(), "A, B, D caught; C missed");
        assertEquals(new ConfusionMatrix(3, 2, 1, 2), at(7).confusion(), "E and F join at T = 7");
        assertEquals(100, at(8).sweep().size());
        assertEquals(new ConfusionMatrix(3, 2, 1, 2), at(8).sweep().get(0).confusion(),
                "T = 1 flags A, B, D, E, F; C, G, H score 0, and 0 is not >= 1");
    }

    @Test
    void recallByPatternAndByGroup() {
        MetricsResult r = at(8);
        Map<String, PatternRecall> byPattern = new LinkedHashMap<>();
        r.patterns().forEach(p -> byPattern.put(p.pattern(), p));
        assertEquals(new PatternRecall("upcoding", 2, 1, 1), byPattern.get("upcoding"));
        assertEquals(new PatternRecall("duplicate_claim", 1, 1, 1), byPattern.get("duplicate_claim"));
        assertEquals(new PatternRecall("phantom_billing", 0, 0, 0), byPattern.get("phantom_billing"));
        assertEquals(8, r.patterns().size());

        assertEquals(List.of(
                new GroupRecall("reference_table", MetricsCalculator.REFERENCE_TABLE_PATTERNS, 1, 1),
                new GroupRecall("statistical", MetricsCalculator.STATISTICAL_PATTERNS, 3, 2)), r.groups());
    }

    @Test
    void precisionByRuleCountsEveryClaimCarryingTheFlag() {
        Map<String, RulePrecision> byRule = new LinkedHashMap<>();
        at(8).rules().forEach(p -> byRule.put(p.rule(), p));
        assertEquals(new RulePrecision("upcoding", 1, 1, 1), byRule.get("upcoding"));
        assertEquals(new RulePrecision("rapid_resubmission", 1, 0, 0), byRule.get("rapid_resubmission"),
                "E is legit; the flag counts even though E scores below T = 8");
        assertEquals(new RulePrecision("amount_clustering", 1, 0, 0), byRule.get("amount_clustering"));
    }

    @Test
    void legitClaimsAreBrokenDownByLabelAndScenario() {
        assertEquals(List.of(
                new LegitScenario("legit_near_miss", "day_30_claims", 1, 0, Map.of()),
                new LegitScenario("legit_noise", "band_noise", 1, 1, Map.of("amount_clustering", 1)),
                new LegitScenario("none", "base", 2, 1, Map.of("rapid_resubmission", 1))), at(7).legit());
    }

    @Test
    void precisionAtKIsCappedByTheDatasetSizeAndComparedWithEqualWeights() {
        List<PrecisionAtK> ranking = at(8).ranking();
        assertEquals(new PrecisionAtK(8, 4, 4), ranking.get(0), "k = 50 capped to the 8 claims");

        // All weights 1: every single-flag claim scores round(100 x 1 / 8) = 13, so amount decides the order.
        List<ScoredClaim> equal = MetricsCalculator.equalWeightRanking(ranked, config);
        assertEquals(List.of("A", "B", "D", "E", "F", "C", "G", "H"),
                equal.stream().map(s -> s.claim().claimId()).toList());
        assertEquals(13, equal.get(0).riskScore());
    }

    @Test
    void missingGroundTruthIsAnError() {
        labels.remove("H");
        assertThrows(IllegalArgumentException.class, () -> at(8));
    }
}
