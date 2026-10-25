package com.papaya.fraud.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Bundle;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.io.ClaimCsv;
import com.papaya.fraud.io.ClaimCsvReader;
import com.papaya.fraud.io.ClaimCsvWriter;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.model.Claim;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Task 13 patterns in the seed-42 dataset: duplicate, rapid re-submission, upcoding, unbundling (all counts too). */
class GeneratorFraudPatternsTest {

    private static final int WINDOW = 7;
    private static Catalog catalog;
    private static GeneratedDataset dataset;
    private static Map<String, Claim> claims;
    private static Map<String, GroundTruthRow> truth;
    private static Map<String, ProviderProfile> providers;

    @BeforeAll
    static void generate() {
        catalog = CatalogLoader.loadDefault();
        dataset = new DatasetGenerator(GeneratorConfigLoader.loadDefault(), catalog).generate(42);
        claims = dataset.claims().stream().collect(Collectors.toMap(Claim::claimId, Function.identity()));
        truth = dataset.groundTruth().stream().collect(Collectors.toMap(GroundTruthRow::claimId, Function.identity()));
        providers = dataset.providers().stream().collect(Collectors.toMap(ProviderProfile::id, Function.identity()));
    }

    private static List<Claim> ofPattern(String pattern) {
        return dataset.groundTruth().stream().filter(g -> g.fraudPattern().equals(pattern))
                .map(g -> claims.get(g.claimId())).toList();
    }

    private static long basePrice(Claim c) {
        return c.procedureCodes().stream().mapToLong(code -> catalog.procedure(code).orElseThrow().basePrice()).sum();
    }

    private static boolean inBand(long amount) {
        return amount >= 47_500 && amount <= 49_999;
    }

    /** Other claims of the same member and diagnosis within the window around the claim, minus the excluded ones. */
    private static List<Claim> neighbours(Claim c, Set<String> excluded) {
        return dataset.claims().stream()
                .filter(o -> !o.claimId().equals(c.claimId()) && !excluded.contains(o.claimId()))
                .filter(o -> o.memberId().equals(c.memberId()) && o.diagnosisCode().equals(c.diagnosisCode()))
                .filter(o -> Math.abs(ChronoUnit.DAYS.between(o.claimDate(), c.claimDate())) <= WINDOW)
                .toList();
    }

    @Test
    void eachPatternHasItsExactCountAndIsLabelledFraud() {
        Map<String, Long> fraud = dataset.groundTruth().stream().filter(GroundTruthRow::isFraud)
                .collect(Collectors.groupingBy(GroundTruthRow::fraudPattern, TreeMap::new, Collectors.counting()));
        assertEquals(Map.of("duplicate_claim", 30L, "rapid_resubmission", 30L, "upcoding", 30L, "unbundling", 25L,
                "phantom_billing", 25L, "weekend_anomaly", 20L, "diagnosis_procedure_mismatch", 20L,
                "amount_clustering", 20L), fraud);
        assertTrue(dataset.groundTruth().stream().filter(g -> !g.isFraud())
                .allMatch(g -> Set.of("none", "legit_noise", "legit_near_miss").contains(g.fraudPattern())),
                "Q7: legit claims carry none, legit_noise or legit_near_miss");
    }

    @Test
    void duplicatesCopyTheirOriginalExactlyAndComeAfterIt() {
        Set<String> originals = new HashSet<>();
        for (Claim copy : ofPattern("duplicate_claim")) {
            GroundTruthRow row = truth.get(copy.claimId());
            Claim original = claims.get(row.relatedClaimId());
            assertEquals("duplicate", row.scenario());
            assertEquals("none", truth.get(original.claimId()).fraudPattern(), "only the copy is fraud (D9)");
            assertTrue(originals.add(original.claimId()), "each original is copied once");
            assertEquals(List.of(original.memberId(), original.providerId(), original.claimDate(),
                    original.diagnosisCode(), original.procedureCodes(), original.submittedAmount()),
                    List.of(copy.memberId(), copy.providerId(), copy.claimDate(), copy.diagnosisCode(),
                            copy.procedureCodes(), copy.submittedAmount()));
            assertTrue(copy.claimId().compareTo(original.claimId()) > 0, copy.claimId() + " must follow its original");
            assertEquals(List.of(), neighbours(copy, Set.of(original.claimId())),
                    "no other same member+diagnosis claim in the window (would add a rapid flag)");
        }
    }

    @Test
    void resubmissionsFollowTheirSourceWithinOneToSevenDays() {
        int sameProvider = 0;
        for (Claim re : ofPattern("rapid_resubmission")) {
            GroundTruthRow row = truth.get(re.claimId());
            Claim source = claims.get(row.relatedClaimId());
            long gap = ChronoUnit.DAYS.between(source.claimDate(), re.claimDate());
            assertTrue(gap >= 1 && gap <= 7, re.claimId() + " gap " + gap);
            assertEquals(source.memberId(), re.memberId());
            assertEquals(source.diagnosisCode(), re.diagnosisCode());
            assertEquals("none", truth.get(source.claimId()).fraudPattern());
            if (re.providerId().equals(source.providerId())) {
                sameProvider++;
            }
            assertFalse(providers.get(re.providerId()).weekdayOnly() && re.isWeekend(),
                    re.claimId() + " is a weekend claim at a weekday-only hospital");
            assertTrue((double) re.submittedAmount() / basePrice(re) <= 1.25 + 0.001, "fresh amount within the cap");
            assertFalse(inBand(re.submittedAmount()));
            assertEquals(List.of(), neighbours(re, Set.of(source.claimId())), re.claimId() + " has extra neighbours");
        }
        assertTrue(sameProvider >= 15 && sameProvider <= 27, sameProvider + " of 30 at the same provider (~70%)");
    }

    @Test
    void upcodedClaimsAreSingleProcedureInLargeNonHostGroupsWithTheirFactors() {
        Map<String, Long> legitSingles = dataset.groundTruth().stream().filter(g -> g.fraudPattern().equals("none"))
                .map(g -> claims.get(g.claimId())).filter(c -> c.procedureCodes().size() == 1)
                .collect(Collectors.groupingBy(c -> c.procedureCodes().get(0), TreeMap::new, Collectors.counting()));
        Map<String, Long> perGroup = new TreeMap<>();
        int strong = 0;
        int mild = 0;
        for (Claim up : ofPattern("upcoding")) {
            String scenario = truth.get(up.claimId()).scenario();
            assertEquals(1, up.procedureCodes().size(), "Q1: single procedure");
            String code = up.procedureCodes().get(0);
            assertFalse(Set.of("P-2009", "P-2010").contains(code), "never a band host");
            assertTrue(legitSingles.getOrDefault(code, 0L) >= 30, code + " group too small");
            perGroup.merge(code, 1L, Long::sum);
            double factor = (double) up.submittedAmount() / basePrice(up);
            if (scenario.equals("upcode_strong")) {
                strong++;
                assertTrue(factor >= 1.5 - 0.01 && factor <= 2.5 + 0.01, up.claimId() + " strong factor " + factor);
            } else {
                mild++;
                assertEquals("upcode_mild", scenario);
                assertTrue(factor >= 1.2 - 0.01 && factor <= 1.6 + 0.01, up.claimId() + " mild factor " + factor);
            }
            assertFalse(inBand(up.submittedAmount()), up.claimId() + " lands in the clustering band");
            assertEquals(List.of(), neighbours(up, Set.of()), up.claimId() + " has same member+diagnosis neighbours");
            assertTrue(catalog.isValidPair(up.diagnosisCode(), code));
        }
        assertEquals(20, strong);
        assertEquals(10, mild);
        assertTrue(perGroup.values().stream().allMatch(n -> n <= 3), "at most 3 per group: " + perGroup);
    }

    @Test
    void unbundledClaimsBillAWholeBundleOrTheBundleWithAComponent() {
        int full = 0;
        int doubleBilled = 0;
        for (Claim u : ofPattern("unbundling")) {
            Set<String> codes = new HashSet<>(u.procedureCodes());
            Bundle bundle = catalog.bundles().stream()
                    .filter(b -> codes.containsAll(b.components()) || codes.contains(b.bundleCode()))
                    .findFirst().orElseThrow();
            String scenario = truth.get(u.claimId()).scenario();
            if (scenario.equals("unbundle_full")) {
                full++;
                assertEquals(Set.copyOf(bundle.components()), codes);
            } else {
                doubleBilled++;
                assertEquals("unbundle_double_billing", scenario);
                assertEquals(2, codes.size());
                assertTrue(codes.contains(bundle.bundleCode()));
            }
            for (String code : codes) {
                assertTrue(catalog.isValidPair(u.diagnosisCode(), code), u.claimId() + ": " + code + " vs diagnosis");
            }
            assertTrue(u.submittedAmount() <= 47_000, u.claimId() + " above the 47,000 cap");
            assertFalse(inBand(u.submittedAmount()));
            assertFalse(providers.get(u.providerId()).weekdayOnly() && u.isWeekend());
            assertEquals(List.of(), neighbours(u, Set.of()));
        }
        assertEquals(20, full);
        assertEquals(5, doubleBilled);
    }

    @Test
    void fullDatasetStillRereadsCleanlyWithConsistentWeekendFlags() {
        LoadResult loaded = new ClaimCsvReader(catalog).read(ClaimCsvWriter.toBytes(dataset.claims()));
        assertEquals(List.of(), loaded.rejected());
        assertEquals(List.of(), loaded.warnings());
        assertTrue(dataset.claims().stream().allMatch(c -> c.isWeekend() == ClaimCsv.isWeekend(c.claimDate())));
        assertNotEquals(0, dataset.claims().size());
    }
}
