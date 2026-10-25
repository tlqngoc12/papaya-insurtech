package com.papaya.fraud.catalog;

import static com.papaya.fraud.model.ClaimType.DENTAL;
import static com.papaya.fraud.model.ClaimType.INPATIENT;
import static com.papaya.fraud.model.ClaimType.OUTPATIENT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validation rules of {@link Catalog#create}, on a small hand-built catalog.
 */
class CatalogTest {

    private final List<Procedure> procedures = new ArrayList<>(List.of(
            new Procedure("P-0001", "Consultation", Set.of(OUTPATIENT, INPATIENT), 300, false),
            new Procedure("P-1001", "CBC", Set.of(OUTPATIENT), 300, false),
            new Procedure("P-1002", "X-ray", Set.of(OUTPATIENT), 600, false),
            new Procedure("P-1100", "Panel", Set.of(OUTPATIENT), 800, false),
            new Procedure("P-2001", "Ward stay", Set.of(INPATIENT), 9000, false)));
    private final List<Diagnosis> diagnoses = new ArrayList<>(List.of(
            new Diagnosis("J06.9", "URTI", Set.of(OUTPATIENT)),
            new Diagnosis("J18.9", "Pneumonia", Set.of(INPATIENT))));
    private final List<Bundle> bundles = new ArrayList<>(List.of(
            new Bundle("P-1100", List.of("P-1001", "P-1002"))));
    private final Map<String, List<String>> valid = new LinkedHashMap<>(Map.of(
            "J06.9", List.of("P-1001", "P-1002", "P-1100"),
            "J18.9", List.of("P-2001")));

    private Catalog create() {
        return Catalog.create(procedures, diagnoses, bundles, new DiagnosisProcedureMap(List.of("P-0001"), valid));
    }

    private List<String> problems() {
        return assertThrows(CatalogException.class, this::create).problems();
    }

    @Test
    void acceptsConsistentCatalog() {
        Catalog catalog = assertDoesNotThrow(this::create);
        assertTrue(catalog.isValidPair("J18.9", "P-0001"));
    }

    @Test
    void rejectsPairWithoutSharedClaimType() {
        valid.put("J18.9", List.of("P-2001", "P-1002"));
        assertEquals(List.of("whitelist J18.9 + P-1002: no shared claim type"), problems());
    }

    @Test
    void rejectsDiagnosisMissingFromWhitelistAndUnknownCodes() {
        diagnoses.add(new Diagnosis("K02.1", "Caries", Set.of(DENTAL)));
        valid.put("J06.9", List.of("P-1001", "P-1002", "P-1100", "P-9999"));
        List<String> problems = problems();
        assertTrue(problems.contains("whitelist: diagnosis K02.1 has no entry"), problems.toString());
        assertTrue(problems.contains("whitelist J06.9: unknown procedure P-9999"), problems.toString());
    }

    @Test
    void rejectsBundleWithWrongComponentCount() {
        bundles.add(new Bundle("P-1002", List.of("P-1001")));
        assertTrue(problems().contains("bundle P-1002: needs 2-4 components, has 1"));
    }

    @Test
    void rejectsBundleWhoseComponentsAreNotValidTogetherForAnyDiagnosis() {
        // P-1002 moves from J06.9 to J18.9, so no diagnosis allows P-1100, P-1001 and P-1002 together.
        procedures.set(2, new Procedure("P-1002", "X-ray", Set.of(OUTPATIENT, INPATIENT), 600, false));
        valid.put("J06.9", List.of("P-1001", "P-1100"));
        valid.put("J18.9", List.of("P-2001", "P-1002"));
        assertEquals(List.of("bundle P-1100: no diagnosis allows the bundle code together with all its components"),
                problems());
    }

    @Test
    void rejectsGenericCodeListedAsSpecific() {
        valid.put("J06.9", List.of("P-1001", "P-1002", "P-1100", "P-0001"));
        assertEquals(List.of("whitelist J06.9: generic code P-0001 must not be listed"), problems());
    }
}
