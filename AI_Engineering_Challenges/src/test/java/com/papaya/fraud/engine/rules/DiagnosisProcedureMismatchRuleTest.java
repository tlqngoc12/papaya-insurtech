package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.catalog.Diagnosis;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.rules.DiagnosisProcedureMismatchRule.Coverage;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import org.junit.jupiter.api.Test;

class DiagnosisProcedureMismatchRuleTest {

    private final Catalog catalog = CatalogLoader.loadDefault();
    private final DiagnosisProcedureMismatchRule rule =
            new DiagnosisProcedureMismatchRule(ConfigLoader.loadDefault(), catalog);

    private boolean flagged(Claim c) {
        return rule.evaluate(c, indexOf(c)).isPresent();
    }

    @Test
    void invalidProcedureIsFlaggedWithDiagnosisAndValidAlternatives() {
        Claim claim = claim("C1").withDiagnosis("J02.9").withProcedures("P-1009").withAmount(900).build();

        Flag flag = rule.evaluate(claim, indexOf(claim)).orElseThrow();
        assertEquals(RuleId.DIAGNOSIS_PROCEDURE_MISMATCH, flag.rule());
        assertEquals(3, flag.severity());
        assertEquals("Procedure P-1009 (Lumbar spine X-ray) is not clinically associated with diagnosis J02.9"
                + " (Acute pharyngitis, unspecified); valid procedures for J02.9: P-0001, P-0002, P-1001, P-1008",
                flag.evidence());
    }

    @Test
    void anyInvalidProcedureAmongValidOnesIsFlaggedAndOnlyInvalidOnesAreListed() {
        Claim claim = claim("C1").withDiagnosis("J02.9").withProcedures("P-1008", "P-1009", "P-1011").build();
        assertTrue(rule.evaluate(claim, indexOf(claim)).orElseThrow().evidence().startsWith(
                "Procedures P-1009 (Lumbar spine X-ray), P-1011 (Slit-lamp eye examination) are not clinically"
                        + " associated with diagnosis J02.9"));
    }

    @Test
    void unknownDiagnosisIsNotFlagged() {
        assertTrue(!flagged(claim("C1").withDiagnosis("Z99.9").withProcedures("P-1009").build()));
    }

    @Test
    void allValidProceduresAreNotFlagged() {
        assertTrue(!flagged(claim("C1").withDiagnosis("I10").withProcedures("P-1005", "P-1006").build()));
    }

    @Test
    void genericConsultationIsValidWithEveryDiagnosisButP0002FollowsClaimTypes() {
        for (Diagnosis dx : catalog.diagnoses()) {
            assertTrue(!flagged(claim("C-" + dx.code()).withDiagnosis(dx.code()).withProcedures("P-0001").build()),
                    "P-0001 with " + dx.code());
        }
        Claim inpatientOnly = claim("C1").withDiagnosis("K35.8").withProcedures("P-0002").build();
        assertEquals("Procedure P-0002 (Follow-up consultation) is not clinically associated with diagnosis K35.8"
                + " (Acute appendicitis, other and unspecified); valid procedures for K35.8: P-0001, P-1001, P-2001,"
                + " P-2003, P-2004, P-2005, P-2006, P-2101",
                rule.evaluate(inpatientOnly, indexOf(inpatientOnly)).orElseThrow().evidence());
    }

    @Test
    void procedureMissingFromTheCatalogIsIgnoredButCounted() {
        Claim onlyUnknown = claim("C1").withDiagnosis("J02.9").withProcedures("P-1008", "P-9999").build();
        Claim unknownAndInvalid = claim("C2").withDiagnosis("J02.9").withProcedures("P-9999", "P-1009").build();
        Claim unknownDiagnosis = claim("C3").withDiagnosis("Z99.9").withProcedures("P-1001").build();
        Claim clean = claim("C4").withMember("M-2").build();

        assertTrue(!flagged(onlyUnknown));
        assertTrue(rule.evaluate(unknownAndInvalid, indexOf(unknownAndInvalid)).orElseThrow().evidence()
                .startsWith("Procedure P-1009 (Lumbar spine X-ray) is not"), "known invalid codes are still judged");
        assertEquals(new Coverage(3, 1, 2), rule.coverage(indexOf(onlyUnknown, unknownAndInvalid, unknownDiagnosis, clean)));
    }
}
