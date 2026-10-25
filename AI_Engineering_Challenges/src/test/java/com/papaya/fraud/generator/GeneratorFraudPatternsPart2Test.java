package com.papaya.fraud.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Bundle;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.generator.GeneratedDataset.ScenarioDay;
import com.papaya.fraud.generator.GeneratorConfig.BandHost;
import com.papaya.fraud.generator.ProviderProfile.Tier;
import com.papaya.fraud.io.ClaimCsvReader;
import com.papaya.fraud.io.ClaimCsvWriter;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
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

/** Task 14 patterns in the seed-42 dataset: phantom billing, weekend anomaly, mismatch, amount clustering. */
class GeneratorFraudPatternsPart2Test {

    private static GeneratorConfig config;
    private static Catalog catalog;
    private static GeneratedDataset dataset;
    private static Map<String, GroundTruthRow> truth;
    private static Map<String, ProviderProfile> providers;

    @BeforeAll
    static void generate() {
        config = GeneratorConfigLoader.loadDefault();
        catalog = CatalogLoader.loadDefault();
        dataset = new DatasetGenerator(config, catalog).generate(42);
        truth = dataset.groundTruth().stream().collect(Collectors.toMap(GroundTruthRow::claimId, Function.identity()));
        providers = dataset.providers().stream().collect(Collectors.toMap(ProviderProfile::id, Function.identity()));
    }

    private static String pattern(Claim c) {
        return truth.get(c.claimId()).fraudPattern();
    }

    private static List<Claim> ofPattern(String pattern) {
        return dataset.claims().stream().filter(c -> pattern(c).equals(pattern)).toList();
    }

    private static boolean neighbourFree(Claim c) {
        return dataset.claims().stream().noneMatch(o -> !o.claimId().equals(c.claimId())
                && o.memberId().equals(c.memberId()) && o.diagnosisCode().equals(c.diagnosisCode())
                && Math.abs(ChronoUnit.DAYS.between(o.claimDate(), c.claimDate())) <= 7);
    }

    @Test
    void phantomDayHasExactlyTwentyFiveFraudClaimsOfDistinctMembersAndNothingElseYet() {
        ScenarioDay day = dataset.phantomDay();
        List<Claim> onDay = dataset.claims().stream()
                .filter(c -> c.providerId().equals(day.providerId()) && c.claimDate().equals(day.date())).toList();
        List<Claim> phantom = ofPattern("phantom_billing");

        assertEquals(25, phantom.size());
        assertEquals(Set.copyOf(phantom), Set.copyOf(onDay), "the reserved day holds only the 25 phantom claims");
        assertEquals(25, phantom.stream().map(Claim::memberId).distinct().count(), "distinct members");
        for (Claim c : phantom) {
            assertEquals(ClaimType.OUTPATIENT, c.claimType());
            assertEquals("phantom_day", truth.get(c.claimId()).scenario());
            assertTrue(c.procedureCodes().stream().allMatch(code -> catalog.isValidPair(c.diagnosisCode(), code)));
            assertTrue(neighbourFree(c), c.claimId());
        }
    }

    @Test
    void weekendSurgeriesSitAtWeekdayOnlyHospitalsBelowFivePercentLeaveOneOut() {
        List<Claim> weekend = ofPattern("weekend_anomaly");
        assertEquals(20, weekend.size());
        Map<String, Long> perHospital = weekend.stream()
                .collect(Collectors.groupingBy(Claim::providerId, TreeMap::new, Collectors.counting()));
        assertEquals(8, perHospital.size());
        assertEquals(List.of(2L, 2L, 2L, 2L, 3L, 3L, 3L, 3L), perHospital.values().stream().sorted().toList());

        for (Claim c : weekend) {
            assertEquals(Tier.WEEKDAY_ONLY_HOSPITAL, providers.get(c.providerId()).tier());
            assertTrue(c.isWeekend(), c.claimId());
            assertFalse(c.procedureCodes().contains("P-2009"));
            assertTrue(c.procedureCodes().stream().anyMatch(code -> catalog.procedure(code).orElseThrow().surgical()));
            assertTrue(neighbourFree(c), c.claimId());
            // Leave-one-out weekend share computed from the generated data itself, not by the engine.
            List<Claim> others = dataset.claims().stream()
                    .filter(o -> o.providerId().equals(c.providerId()) && !o.claimId().equals(c.claimId())).toList();
            long weekendOthers = others.stream().filter(Claim::isWeekend).count();
            assertTrue(others.size() >= 20, "history");
            assertTrue((double) weekendOthers / others.size() < 0.05,
                    c.claimId() + ": " + weekendOthers + " of " + others.size());
        }
        for (ProviderProfile p : dataset.providers()) {
            if (p.weekdayOnly()) {
                assertTrue(dataset.claims().stream().filter(c -> c.providerId().equals(p.id()) && c.isWeekend())
                        .allMatch(c -> pattern(c).equals("weekend_anomaly")), p.id() + " has another weekend claim");
            }
        }
    }

    @Test
    void mismatchesHaveExactlyOneInvalidProcedureAndNoBundleOrHostCode() {
        List<Claim> mismatches = ofPattern("diagnosis_procedure_mismatch");
        assertEquals(20, mismatches.size());
        for (Claim c : mismatches) {
            long invalid = c.procedureCodes().stream().filter(code -> !catalog.isValidPair(c.diagnosisCode(), code)).count();
            assertEquals(1, invalid, c.claimId() + " " + c.procedureCodes());
            for (String code : c.procedureCodes()) {
                assertTrue(catalog.procedure(code).orElseThrow().claimTypes().contains(c.claimType()), c.claimId());
            }
            assertFalse(c.procedureCodes().contains("P-2009") || c.procedureCodes().contains("P-2010"));
            Set<String> codes = new HashSet<>(c.procedureCodes());
            for (Bundle b : catalog.bundles()) {
                assertFalse(codes.containsAll(b.components()), c.claimId() + " completes " + b.bundleCode());
                assertFalse(codes.contains(b.bundleCode()) && b.components().stream().anyMatch(codes::contains),
                        c.claimId() + " double-bills " + b.bundleCode());
            }
            assertFalse(providers.get(c.providerId()).weekdayOnly() && c.isWeekend());
            assertFalse(c.submittedAmount() >= 47_500 && c.submittedAmount() <= 49_999);
            assertTrue(neighbourFree(c), c.claimId());
        }
    }

    @Test
    void clusteringClaimsAreFivePerBandHostInsideTheRangeAndKeepThePlantedShareUnderAQuarter() {
        List<Claim> band = ofPattern("amount_clustering");
        assertEquals(20, band.size());
        for (BandHost host : config.bandHosts()) {
            List<Claim> inHost = band.stream().filter(c -> c.diagnosisCode().equals(host.diagnosis())
                    && c.procedureCodes().equals(host.procedures())).toList();
            assertEquals(5, inHost.size(), host.procedures().toString());
            // Planted claims once Task 15 adds its 5 noise claims per host, over the final group size.
            int plantedFinal = host.plantedClaims();
            double share = (double) plantedFinal / (host.naturalClaims() + plantedFinal);
            assertTrue(share <= 0.25, host + ": " + share);
            assertEquals(10, plantedFinal);
        }
        for (Claim c : band) {
            assertTrue(c.submittedAmount() >= 47_500 && c.submittedAmount() <= 49_999, c.claimId());
            assertEquals(ClaimType.INPATIENT, c.claimType());
            assertTrue(providers.get(c.providerId()).hospital());
            if (c.procedureCodes().contains("P-2009")) {
                assertFalse(providers.get(c.providerId()).weekdayOnly() && c.isWeekend(),
                        c.claimId() + ": surgical P-2009 on a weekend at a weekday-only hospital");
            }
            assertTrue(neighbourFree(c), c.claimId());
        }
    }

    @Test
    void fullDatasetRereadsCleanly() {
        LoadResult loaded = new ClaimCsvReader(catalog).read(ClaimCsvWriter.toBytes(dataset.claims()));
        assertEquals(List.of(), loaded.rejected());
        assertEquals(List.of(), loaded.warnings());
        assertEquals(1_919, loaded.claims().size());
    }
}
