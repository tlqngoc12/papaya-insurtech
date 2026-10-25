package com.papaya.fraud.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.testing.TestConfigs;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    private final FraudConfig config = ConfigLoader.loadDefault();
    private final ScoreCalculator calculator = new ScoreCalculator(config);

    private List<Flag> flags(RuleId... rules) {
        return Arrays.stream(rules).map(r -> new Flag(r, config.weight(r), "test")).toList();
    }

    @Test
    void scoresFollowS1aWithTotalWeight28() {
        assertEquals(0, calculator.score(List.of()), "no rule");
        assertEquals(18, calculator.score(flags(RuleId.DUPLICATE_CLAIM)), "{5}: 100 x 5 / 28 = 17.86");
        assertEquals(21, calculator.score(flags(RuleId.UPCODING, RuleId.AMOUNT_CLUSTERING)),
                "{4, 2}: 100 x 6 / 28 = 21.43");
        assertEquals(7, calculator.score(flags(RuleId.RAPID_RESUBMISSION)), "{2}: 7.14");
        assertEquals(25, calculator.score(flags(RuleId.PHANTOM_BILLING, RuleId.AMOUNT_CLUSTERING)), "{5, 2}: 25 exactly");
        assertEquals(100, calculator.score(flags(RuleId.values())), "all eight rules");
    }

    @Test
    void roundsHalfUpInIntegerArithmetic() {
        FraudConfig ones = TestConfigs.defaultWith(t -> {
            ObjectNode weights = (ObjectNode) t.get("weights");
            for (RuleId r : RuleId.values()) {
                weights.put(r.id(), 1);
            }
        });
        ScoreCalculator equal = new ScoreCalculator(ones);
        assertEquals(8, ones.totalWeight());
        assertEquals(13, equal.score(List.of(new Flag(RuleId.UPCODING, 1, "x"))), "100 x 1 / 8 = 12.5 -> 13");
        assertEquals(38, equal.score(List.of(new Flag(RuleId.UPCODING, 1, "x"), new Flag(RuleId.UNBUNDLING, 1, "x"),
                new Flag(RuleId.DUPLICATE_CLAIM, 1, "x"))), "100 x 3 / 8 = 37.5 -> 38");
    }

    @Test
    void sameRuleTwiceOnOneClaimIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.score(flags(RuleId.UPCODING, RuleId.UPCODING)), "S6: at most one flag per rule");
    }
}
