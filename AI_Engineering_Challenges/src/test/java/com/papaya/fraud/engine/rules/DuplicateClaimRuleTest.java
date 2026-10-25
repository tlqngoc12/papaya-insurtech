package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DuplicateClaimRuleTest {

    private final DuplicateClaimRule rule = new DuplicateClaimRule(ConfigLoader.loadDefault());

    @Test
    void secondClaimWithSameFourKeysIsFlaggedWithEvidenceAndFirstIsNot() {
        Claim original = claim("CLM-00042").withAmount(1_200).build();
        Claim copy = claim("CLM-00043").withAmount(1_200).build();
        DatasetIndex index = indexOf(copy, original);

        assertTrue(rule.evaluate(original, index).isEmpty(), "the first claim may be genuine (D9)");
        Flag flag = rule.evaluate(copy, index).orElseThrow();
        assertEquals(RuleId.DUPLICATE_CLAIM, flag.rule());
        assertEquals(5, flag.severity(), "severity is the configured weight");
        assertEquals("Duplicate of CLM-00042 (occurrence 2 of 2): same member M-0001, provider PRV-01,"
                + " date 2024-03-11 and diagnosis J06.9; submitted 1,200 vs 1,200 on the original", flag.evidence());
    }

    @Test
    void differentDiagnosisProviderOrDateIsNotADuplicate() {
        Claim first = claim("C1").build();
        Claim otherDiagnosis = claim("C2").withDiagnosis("J02.9").build();
        Claim otherProvider = claim("C3").withProvider("PRV-02").build();
        Claim otherDate = claim("C4").withDate("2024-03-12").build();
        DatasetIndex index = indexOf(first, otherDiagnosis, otherProvider, otherDate);

        assertEquals(Optional.empty(), rule.evaluate(otherDiagnosis, index));
        assertEquals(Optional.empty(), rule.evaluate(otherProvider, index));
        assertEquals(Optional.empty(), rule.evaluate(otherDate, index));
    }

    @Test
    void groupOfThreeFlagsTheSecondAndThirdAgainstTheFirst() {
        Claim c1 = claim("C1").build();
        Claim c2 = claim("C2").withProcedures("P-1002").withAmount(600).build();
        Claim c3 = claim("C3").build();
        DatasetIndex index = indexOf(c3, c2, c1);

        assertTrue(rule.evaluate(c1, index).isEmpty());
        assertTrue(rule.evaluate(c2, index).orElseThrow().evidence()
                .startsWith("Duplicate of C1 (occurrence 2 of 3)"));
        assertTrue(rule.evaluate(c3, index).orElseThrow().evidence()
                .startsWith("Duplicate of C1 (occurrence 3 of 3)"));
        assertTrue(rule.evaluate(c2, index).orElseThrow().evidence().endsWith("submitted 600 vs 300 on the original"),
                "procedures and amount are not part of the key");
    }
}
