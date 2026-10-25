package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.MONDAY;
import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import org.junit.jupiter.api.Test;

class RapidResubmissionRuleTest {

    private final FraudConfig config = ConfigLoader.loadDefault();
    private final RapidResubmissionRule rule = new RapidResubmissionRule(config);

    @Test
    void sevenDaysLaterIsFlaggedAtTheWindowBoundary() {
        Claim first = claim("CLM-00001").build();
        Claim later = claim("CLM-00002").withProvider("PRV-02").withDate(MONDAY.plusDays(7)).build();
        DatasetIndex index = indexOf(first, later);

        Flag flag = rule.evaluate(later, index).orElseThrow();
        assertEquals(RuleId.RAPID_RESUBMISSION, flag.rule());
        assertEquals(2, flag.severity());
        assertEquals("Re-submitted 7 days after CLM-00001 (2024-03-11, provider PRV-01) for the same member M-0001"
                + " and diagnosis J06.9; 1 earlier claim within the 7-day window", flag.evidence());
    }

    @Test
    void eightDaysLaterIsNotFlagged() {
        Claim first = claim("C1").build();
        Claim later = claim("C2").withDate(MONDAY.plusDays(8)).build();
        assertTrue(rule.evaluate(later, indexOf(first, later)).isEmpty());
    }

    @Test
    void firstClaimOfThePairAndOtherDiagnosisAreNotFlagged() {
        Claim first = claim("C1").build();
        Claim later = claim("C2").withDate(MONDAY.plusDays(2)).build();
        Claim otherDiagnosis = claim("C3").withDiagnosis("J02.9").withDate(MONDAY.plusDays(1)).build();
        DatasetIndex index = indexOf(first, later, otherDiagnosis);

        assertTrue(rule.evaluate(first, index).isEmpty());
        assertTrue(rule.evaluate(otherDiagnosis, index).isEmpty());
        assertTrue(rule.evaluate(later, index).isPresent());
    }

    @Test
    void sameDayAtAnotherProviderIsFlaggedButSameDaySameProviderIsLeftToDuplicate() {
        Claim first = claim("C1").build();
        Claim otherProvider = claim("C2").withProvider("PRV-02").build();
        Claim duplicate = claim("C3").build();
        DatasetIndex index = indexOf(first, otherProvider, duplicate);

        assertEquals("Re-submitted 0 days after C1 (2024-03-11, provider PRV-01) for the same member M-0001"
                        + " and diagnosis J06.9; 1 earlier claim within the 7-day window",
                rule.evaluate(otherProvider, index).orElseThrow().evidence());
        // C3 duplicates C1; its only other earlier claim is C2 at another provider on the same day.
        assertEquals("Re-submitted 0 days after C2 (2024-03-11, provider PRV-02) for the same member M-0001"
                        + " and diagnosis J06.9; 1 earlier claim within the 7-day window",
                rule.evaluate(duplicate, index).orElseThrow().evidence());

        Claim onlyDuplicate = claim("D2").build();
        assertTrue(rule.evaluate(onlyDuplicate, indexOf(first, onlyDuplicate)).isEmpty(),
                "a pure duplicate pair is scored once, by duplicate_claim");
    }

    @Test
    void chainOfDayZeroThreeSixFlagsTheLastTwo() {
        Claim d0 = claim("C1").build();
        Claim d3 = claim("C2").withDate(MONDAY.plusDays(3)).build();
        Claim d6 = claim("C3").withDate(MONDAY.plusDays(6)).build();
        DatasetIndex index = indexOf(d6, d3, d0);

        assertTrue(rule.evaluate(d0, index).isEmpty());
        assertTrue(rule.evaluate(d3, index).orElseThrow().evidence().startsWith("Re-submitted 3 days after C1 "));
        assertTrue(rule.evaluate(d6, index).orElseThrow().evidence().startsWith("Re-submitted 3 days after C2 "),
                "evidence cites the closest earlier claim");
        assertTrue(rule.evaluate(d6, index).orElseThrow().evidence().endsWith("2 earlier claims within the 7-day window"));
    }
}
