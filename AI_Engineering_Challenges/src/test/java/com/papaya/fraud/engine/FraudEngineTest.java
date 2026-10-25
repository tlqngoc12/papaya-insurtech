package com.papaya.fraud.engine;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.rules.Rule;
import com.papaya.fraud.io.ScoredOutputWriter;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import com.papaya.fraud.testing.RandomClaims;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FraudEngineTest {

    private final FraudEngine engine = new FraudEngine(ConfigLoader.loadDefault(), CatalogLoader.loadDefault());

    @Test
    void engineRunsExactlyOneRulePerRuleIdInRuleIdOrder() {
        List<RuleId> ids = engine.rules().stream().map(Rule::id).toList();
        assertEquals(List.of(RuleId.values()), ids);
        assertEquals(EnumSet.allOf(RuleId.class), EnumSet.copyOf(ids));
    }

    @Test
    void everyClaimIsScoredOnceWithAtMostOneFlagPerRule() {
        // Matches two bundles (unbundling) and two invalid procedures (mismatch): still one flag each (S6).
        Claim busy = claim("C1").withType(ClaimType.INPATIENT).withDiagnosis("J06.9")
                .withProcedures("P-2006", "P-2007", "P-2004", "P-2005").withAmount(48_000).build();
        Claim copy = claim("C2").withType(ClaimType.INPATIENT).withDiagnosis("J06.9")
                .withProcedures("P-2006", "P-2007", "P-2004", "P-2005").withAmount(48_000).build();
        FraudEngine.Result result = engine.run(List.of(copy, busy, claim("C3").withMember("M-9").build()));

        assertEquals(3, result.ranked().size());
        for (ScoredClaim s : result.ranked()) {
            Set<RuleId> rules = EnumSet.noneOf(RuleId.class);
            for (Flag f : s.flags()) {
                assertTrue(rules.add(f.rule()), s.claim().claimId() + " has two " + f.rule() + " flags");
            }
            List<RuleId> order = s.flags().stream().map(Flag::rule).toList();
            assertEquals(order.stream().sorted().toList(), order, "flags in RuleId order");
        }
        ScoredClaim top = result.ranked().get(0);
        assertEquals("C2", top.claim().claimId());
        assertEquals(List.of(RuleId.DUPLICATE_CLAIM, RuleId.UNBUNDLING, RuleId.DIAGNOSIS_PROCEDURE_MISMATCH,
                RuleId.AMOUNT_CLUSTERING), top.flags().stream().map(Flag::rule).toList());
        assertEquals(50, top.riskScore(), "5 + 4 + 3 + 2 = 14 of 28");
    }

    @Test
    void scoresTwoThousandClaimsWellUnderThirtySeconds() {
        List<Claim> claims = RandomClaims.generate(2_000, 1);
        byte[] output = assertTimeout(Duration.ofSeconds(30), () -> {
            long start = System.nanoTime();
            FraudEngine.Result result = engine.run(claims);
            byte[] bytes = ScoredOutputWriter.toBytes(result.ranked());
            System.out.printf("FraudEngine: scored and serialized 2,000 claims in %d ms%n",
                    (System.nanoTime() - start) / 1_000_000);
            return bytes;
        });
        assertTrue(output.length > 0);
    }

    @Test
    void scoresOneHundredThousandClaims() {
        List<Claim> claims = RandomClaims.generate(100_000, 2);
        // Q19: under 30 seconds, time printed.
        FraudEngine.Result result = assertTimeout(Duration.ofSeconds(30), () -> {
            long start = System.nanoTime();
            FraudEngine.Result r = engine.run(claims);
            System.out.printf("FraudEngine: scored 100,000 claims in %d ms%n", (System.nanoTime() - start) / 1_000_000);
            return r;
        });
        assertEquals(100_000, result.ranked().size());
    }
}
