package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.rules.UnbundlingRule.Coverage;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import org.junit.jupiter.api.Test;

class UnbundlingRuleTest {

    private final UnbundlingRule rule = new UnbundlingRule(ConfigLoader.loadDefault(), CatalogLoader.loadDefault());

    private Flag flagOf(Claim c) {
        return rule.evaluate(c, indexOf(c)).orElseThrow();
    }

    private boolean flagged(Claim c) {
        return rule.evaluate(c, indexOf(c)).isPresent();
    }

    @Test
    void allComponentsBilledSeparatelyIsFlaggedWhateverTheDiagnosis() {
        Claim appendicitis = claim("C1").withType(ClaimType.INPATIENT).withDiagnosis("K35.8")
                .withProcedures("P-2006", "P-2004", "P-2005").withAmount(35_000).build();

        Flag flag = flagOf(appendicitis);
        assertEquals(RuleId.UNBUNDLING, flag.rule());
        assertEquals(4, flag.severity());
        assertEquals("Unbundling on 1 bundle: all components of bundle P-2101 billed separately: P-2006, P-2004, P-2005"
                + " (35,000 at base price vs bundle price 30,000)", flag.evidence());

        Claim otherDiagnosis = claim("C2").withDiagnosis("J06.9").withProcedures("P-2005", "P-2006", "P-2004").build();
        assertTrue(flagged(otherDiagnosis), "bundles are checked regardless of diagnosis");
    }

    @Test
    void missingOneComponentIsNotFlagged() {
        assertTrue(!flagged(claim("C1").withDiagnosis("K35.8").withProcedures("P-2006", "P-2004").build()));
    }

    @Test
    void bundleCodeWithAnyOfItsComponentsIsDoubleBilling() {
        Claim claim = claim("C1").withDiagnosis("E11.9").withProcedures("P-1101", "P-1004").withAmount(1_700).build();

        Flag flag = flagOf(claim);
        assertEquals(RuleId.UNBUNDLING, flag.rule(), "same rule, only the evidence label differs");
        assertEquals(4, flag.severity());
        assertEquals("Double billing on 1 bundle: bundle P-1101 billed together with its component P-1004"
                + " (500 at base price on top of the bundle price 1,200)", flag.evidence());
        assertTrue(!flagged(claim("C2").withDiagnosis("E11.9").withProcedures("P-1101").build()),
                "the bundle code alone is the correct way to bill");
    }

    @Test
    void claimWithBothKindsUsesACombinedLabel() {
        Claim claim = claim("C1").withDiagnosis("E11.9").withProcedures("P-1101", "P-1004", "P-1003", "P-1007").build();

        assertEquals("Unbundling and double billing on 2 bundles: bundle P-1101 billed together with its components"
                + " P-1004, P-1003 (850 at base price on top of the bundle price 1,200); all components of bundle"
                + " P-1103 billed separately: P-1003, P-1007 (1,150 at base price vs bundle price 950)",
                flagOf(claim).evidence());
    }

    @Test
    void legitimatePartialComponentsAreNotFlagged() {
        // I10 with ECG and lipid panel but no CBC: two of the three components of P-1102.
        assertTrue(!flagged(claim("C1").withDiagnosis("I10").withProcedures("P-1005", "P-1006").build()));
    }

    @Test
    void claimMatchingTwoBundlesHasOneFlagListingBoth() {
        Claim claim = claim("C1").withType(ClaimType.INPATIENT).withDiagnosis("K80.2")
                .withProcedures("P-2006", "P-2007", "P-2004", "P-2005").build();

        assertEquals("Unbundling on 2 bundles: all components of bundle P-2101 billed separately: P-2006, P-2004,"
                + " P-2005 (35,000 at base price vs bundle price 30,000); all components of bundle P-2102 billed"
                + " separately: P-2007, P-2004, P-2005 (37,000 at base price vs bundle price 32,000)",
                flagOf(claim).evidence());
    }

    @Test
    void coverageCountsSingleProcedureClaimsAsSkipped() {
        Claim single = claim("C1").build();
        Claim repeated = claim("C2").withProcedures("P-1001", "P-1001").build();
        Claim pair = claim("C3").withProcedures("P-1001", "P-1002").build();
        assertEquals(new Coverage(1, 2), rule.coverage(indexOf(single, repeated, pair)));
    }
}
