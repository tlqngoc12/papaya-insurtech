package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.rules.UpcodingRule.Coverage;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.testing.TestConfigs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpcodingRuleTest {

    private final UpcodingRule rule = new UpcodingRule(ConfigLoader.loadDefault());

    /**
     * Ten single-procedure claims: deviations from 1,000 are -40, -10, -10, 0, 0, 0, 0, +10, +10 and the last one.
     * With the last at 1,040 the sum of squares is 3,600, so mean = 1,000 and SD = sqrt(3,600 / 9) = 20 exactly,
     * and z = 40 / 20 = 2.0 exactly in double arithmetic.
     */
    private static List<Claim> group(String code, long last) {
        long[] amounts = {960, 990, 990, 1000, 1000, 1000, 1000, 1010, 1010, last};
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            claims.add(claim(code + "-" + i).withMember("M-" + i).withProcedures(code).withAmount(amounts[i]).build());
        }
        return claims;
    }

    private static Claim last(List<Claim> claims) {
        return claims.get(claims.size() - 1);
    }

    @Test
    void zJustAboveTwoIsFlaggedWithEvidence() {
        List<Claim> claims = group("P-2101", 1_041);
        DatasetIndex index = indexOf(claims);

        Flag flag = rule.evaluate(last(claims), index).orElseThrow();
        assertEquals(RuleId.UPCODING, flag.rule());
        assertEquals(4, flag.severity());
        assertEquals("Submitted amount 1,041 for procedure P-2101 is 2.02 standard deviations above the mean of"
                + " 1,000.1 (SD 20.2, n = 10 single-procedure claims of P-2101; threshold z > 2.0)", flag.evidence());
        assertTrue(rule.evaluate(claims.get(0), index).isEmpty(), "an amount below the mean is never upcoding");
    }

    @Test
    void zOfExactlyTwoIsNotFlagged() {
        List<Claim> claims = group("P-2101", 1_040);
        DatasetIndex index = indexOf(claims);

        assertEquals(2.0, UpcodingRule.zScore(1_040, index.signatureStats("P-2101").orElseThrow()), 0.0,
                "exactly 2.0, no rounding involved");
        assertTrue(rule.evaluate(last(claims), index).isEmpty(), "R10: z > 2.0 is strict");
    }

    @Test
    void smallGroupsAndZeroSdAreSkippedAndCounted() {
        List<Claim> claims = new ArrayList<>(group("P-2101", 1_041).subList(1, 10)); // 9 claims, below n_min
        claims.add(claim("BIG").withMember("M-X").withProcedures("P-2009").withAmount(42_000).build());
        for (int i = 0; i < 10; i++) {
            claims.add(claim("FLAT-" + i).withMember("M-F" + i).withProcedures("P-1005").withAmount(700).build());
        }
        DatasetIndex index = indexOf(claims);

        assertTrue(rule.evaluate(claims.get(8), index).isEmpty(), "n = 9 < 10: not judged");
        assertTrue(rule.evaluate(claims.get(9), index).isEmpty(), "n = 1: SD is NaN, below n_min too");
        assertTrue(rule.evaluate(claims.get(10), index).isEmpty(), "SD = 0: no deviation to measure");
        assertEquals(new Coverage(0, 0, 10, 10), rule.coverage(index));
    }

    @Test
    void multiProcedureClaimsAreSkippedEvenWhenExtreme() {
        List<Claim> claims = new ArrayList<>(group("P-2101", 1_000));
        for (int i = 0; i < 10; i++) {
            long amount = i == 9 ? 1_000_000 : 21_000;
            claims.add(claim("PAIR-" + i).withMember("M-P" + i).withProcedures("P-2001", "P-2002").withAmount(amount)
                    .build());
        }
        DatasetIndex index = indexOf(claims);

        assertTrue(rule.evaluate(last(claims), index).isEmpty(), "Q1: only single-procedure claims are judged");
        assertEquals(new Coverage(10, 10, 0, 0), rule.coverage(index));
    }

    /**
     * Masking (L9): mean and SD include the claim itself, so in a group of n the largest possible z is
     * (n - 1) / sqrt(n). For n = 5 that is 4 / sqrt(5) = 1.789, whatever the amount; for n = 10 it is 2.846.
     */
    @Test
    void maskingCapsZInSmallGroups() {
        UpcodingRule permissive = new UpcodingRule(TestConfigs.withUpcodingMinGroupSize(2));
        for (long extreme : new long[] {1_000L, 1_000_000L, 1_000_000_000_000L}) {
            List<Claim> five = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                five.add(claim("N5-" + i).withMember("M-" + i).withProcedures("P-1002").withAmount(100).build());
            }
            five.add(claim("N5-X").withMember("M-X").withProcedures("P-1002").withAmount(extreme).build());
            DatasetIndex index = indexOf(five);

            double z = UpcodingRule.zScore(extreme, index.signatureStats("P-1002").orElseThrow());
            assertTrue(z < 1.79, "n = 5, amount " + extreme + ": z = " + z);
            assertEquals(4 / Math.sqrt(5), z, 1e-9);
            assertTrue(permissive.evaluate(last(five), index).isEmpty());
        }

        List<Claim> ten = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ten.add(claim("N10-" + i).withMember("M-" + i).withProcedures("P-1002").withAmount(100).build());
        }
        ten.add(claim("N10-X").withMember("M-X").withProcedures("P-1002").withAmount(1_000_000).build());
        DatasetIndex index = indexOf(ten);
        assertEquals(9 / Math.sqrt(10), UpcodingRule.zScore(1_000_000, index.signatureStats("P-1002").orElseThrow()),
                1e-9);
        assertTrue(rule.evaluate(last(ten), index).isPresent(), "n = 10 can exceed 2: max z = 2.846");
    }
}
