package com.papaya.fraud.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.model.ClaimType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the shipped catalog against the approved draft (Task 02) and the challenge requirements.
 * Pair counts are computed here by iterating the data, never read from the code under test.
 */
class CatalogLoaderTest {

    private static Catalog catalog;

    @BeforeAll
    static void load() {
        catalog = CatalogLoader.loadDefault();
    }

    private static boolean shareClaimType(Set<ClaimType> a, Set<ClaimType> b) {
        EnumSet<ClaimType> common = EnumSet.copyOf(a);
        common.retainAll(b);
        return !common.isEmpty();
    }

    @Test
    void loadsApprovedSizesAndSurgicalProcedures() {
        assertEquals(21, catalog.diagnoses().size());
        assertEquals(40, catalog.procedures().size());
        assertEquals(6, catalog.bundles().size());

        Set<String> surgical = catalog.procedures().stream()
                .filter(Procedure::surgical).map(Procedure::code).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of("P-2006", "P-2007", "P-2009", "P-2012", "P-2013", "P-2101", "P-2102", "P-3007"),
                surgical);
    }

    @Test
    void everySpecificPairSharesAClaimTypeAndCountMatchesHandCount() {
        int pairs = 0;
        for (Diagnosis dx : catalog.diagnoses()) {
            for (String code : catalog.specificProcedures(dx.code())) {
                Procedure p = catalog.procedure(code).orElseThrow();
                assertTrue(shareClaimType(dx.claimTypes(), p.claimTypes()),
                        dx.code() + " + " + code + " share no claim type");
                pairs++;
            }
        }
        assertEquals(64, pairs, "specific diagnosis-procedure pairs");
        assertTrue(pairs >= 10, "challenge requires at least 10 valid pairs");
    }

    @Test
    void genericCodesApplyOnlyToDiagnosesSharingAClaimType() {
        int pairs = 0;
        for (Diagnosis dx : catalog.diagnoses()) {
            Set<String> expected = new TreeSet<>();
            for (String code : catalog.genericCodes()) {
                if (shareClaimType(dx.claimTypes(), catalog.procedure(code).orElseThrow().claimTypes())) {
                    expected.add(code);
                    pairs++;
                }
            }
            assertEquals(expected, catalog.genericProceduresFor(dx.code()), dx.code());
        }
        assertEquals(35, pairs, "generic pairs: P-0001 x 21 + P-0002 x 14");

        assertTrue(catalog.isValidPair("K35.8", "P-0001"));
        assertFalse(catalog.isValidPair("K35.8", "P-0002"), "P-0002 is OUTPATIENT/DENTAL only");
        assertTrue(catalog.isValidPair("K02.1", "P-0002"));
    }

    @Test
    void whitelistCoversEveryDiagnosisAndUsesEveryProcedure() {
        for (Diagnosis dx : catalog.diagnoses()) {
            assertTrue(catalog.hasWhitelistFor(dx.code()), dx.code());
            assertFalse(catalog.specificProcedures(dx.code()).isEmpty(), dx.code() + " has no specific procedure");
        }
        Set<String> used = new TreeSet<>(catalog.genericCodes());
        catalog.diagnoses().forEach(dx -> used.addAll(catalog.specificProcedures(dx.code())));
        Set<String> all = catalog.procedures().stream().map(Procedure::code)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(all, used, "every procedure appears in at least one pair");
    }

    @Test
    void bundlesMeetChallengeAndAreCheaperThanTheirComponents() {
        assertTrue(catalog.bundles().size() >= 5, "challenge requires at least 5 bundles");
        for (Bundle b : catalog.bundles()) {
            int n = b.components().size();
            assertTrue(n >= 2 && n <= 4, b.bundleCode() + " has " + n + " components");
            long bundlePrice = catalog.procedure(b.bundleCode()).orElseThrow().basePrice();
            long componentsPrice = b.components().stream()
                    .mapToLong(c -> catalog.procedure(c).orElseThrow().basePrice()).sum();
            assertTrue(bundlePrice < componentsPrice,
                    b.bundleCode() + ": bundle " + bundlePrice + " >= components " + componentsPrice);
            boolean billableTogether = catalog.diagnoses().stream().anyMatch(dx ->
                    catalog.isValidPair(dx.code(), b.bundleCode())
                            && b.components().stream().allMatch(c -> catalog.isValidPair(dx.code(), c)));
            assertTrue(billableTogether, b.bundleCode() + " components not valid for any of its diagnoses");
        }
    }

    /**
     * Task 02 (b) and Q3: with CV 15% the upcoding cut-off is about 1.3 x mean, so an amount of 49,999 stays
     * below z = 2 only if the mean is at least 38,461. Exactly P-2009 and P-2010 host the clustering band; every
     * other inpatient price keeps its whole +-3 sigma range (0.55..1.45 x mean) outside 47,500-49,999.
     */
    @Test
    void inpatientPricesKeepTheClusteringBandToTwoProcedures() {
        double cv = 0.15;
        long bandLow = 47_500;
        long bandHigh = 49_999;
        Set<String> clusteringHosts = new TreeSet<>();
        for (Procedure p : catalog.procedures()) {
            if (!p.claimTypes().contains(ClaimType.INPATIENT)) {
                continue;
            }
            long mean = p.basePrice();
            boolean canHostBand = mean * (1 + 2 * cv) >= bandHigh && mean <= 45_000;
            if (canHostBand) {
                clusteringHosts.add(p.code());
            } else {
                boolean clearOfBand = mean * (1 + 3 * cv) < bandLow || mean * (1 - 3 * cv) > bandHigh;
                assertTrue(clearOfBand, p.code() + " (" + mean + ") reaches the clustering band");
            }
        }
        assertEquals(Set.of("P-2009", "P-2010"), clusteringHosts);
    }

    @Test
    void pairLookupsMatchApprovedEdits() {
        assertTrue(catalog.isValidPair("K35.8", "P-1001"), "CBC is standard for acute appendicitis");
        assertTrue(catalog.isValidPair("J06.9", "P-1002"));
        assertFalse(catalog.isValidPair("J02.9", "P-1009"), "lumbar X-ray is not associated with pharyngitis");
        assertFalse(catalog.hasWhitelistFor("Z99.9"));
        assertFalse(catalog.isValidPair("Z99.9", "P-0001"), "unknown diagnosis has no valid procedures");
        assertEquals(List.of("P-0001", "P-2001", "P-2009"), List.copyOf(catalog.validProcedures("O82")),
                "valid procedures come back sorted");
    }
}
