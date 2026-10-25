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

/** Task 15 in the seed-42 dataset: legit noise, near misses, the 30-claim day, and the final 2,000-claim totals. */
class GeneratorNoiseAndNearMissTest {

    private static GeneratorConfig config;
    private static Catalog catalog;
    private static GeneratedDataset dataset;
    private static Map<String, Claim> claims;
    private static Map<String, GroundTruthRow> truth;
    private static Map<String, ProviderProfile> providers;

    @BeforeAll
    static void generate() {
        config = GeneratorConfigLoader.loadDefault();
        catalog = CatalogLoader.loadDefault();
        dataset = new DatasetGenerator(config, catalog).generate(42);
        claims = dataset.claims().stream().collect(Collectors.toMap(Claim::claimId, Function.identity()));
        truth = dataset.groundTruth().stream().collect(Collectors.toMap(GroundTruthRow::claimId, Function.identity()));
        providers = dataset.providers().stream().collect(Collectors.toMap(ProviderProfile::id, Function.identity()));
    }

    private static List<Claim> ofScenario(String scenario) {
        return dataset.groundTruth().stream().filter(g -> g.scenario().equals(scenario))
                .map(g -> claims.get(g.claimId())).toList();
    }

    private static long basePrice(Claim c) {
        return c.procedureCodes().stream().mapToLong(code -> catalog.procedure(code).orElseThrow().basePrice()).sum();
    }

    @Test
    void datasetHasExactlyTwoThousandClaimsWithTheSpecifiedLabelsAndScenarios() {
        assertEquals(2_000, dataset.claims().size());
        assertEquals(200, dataset.groundTruth().stream().filter(GroundTruthRow::isFraud).count());
        Map<String, Long> labels = dataset.groundTruth().stream().filter(g -> !g.isFraud())
                .collect(Collectors.groupingBy(GroundTruthRow::fraudPattern, TreeMap::new, Collectors.counting()));
        assertEquals(Map.of("none", 1_719L, "legit_noise", 26L, "legit_near_miss", 55L), labels);

        Map<String, Long> scenarios = dataset.groundTruth().stream().filter(g -> !g.fraudPattern().equals("none"))
                .filter(g -> !g.isFraud())
                .collect(Collectors.groupingBy(GroundTruthRow::scenario, TreeMap::new, Collectors.counting()));
        assertEquals(Map.of(
                "band_noise", 20L, "phantom_day_legit", 6L,
                "near_miss_band_below", 3L, "near_miss_band_above", 3L,
                "near_miss_resubmission_8_days", 4L, "near_miss_same_day_other_diagnosis", 3L,
                "near_miss_weekend_surgery", 4L, "near_miss_partial_bundle", 4L, "near_miss_mild_amount", 4L,
                "day_30_claims", 30L), scenarios);
    }

    @Test
    void bandHostsHoldThirtyTwoNaturalClaimsAndAtMostAQuarterPlanted() {
        for (BandHost host : config.bandHosts()) {
            List<GroundTruthRow> rows = dataset.groundTruth().stream().filter(g -> {
                Claim c = claims.get(g.claimId());
                return c.diagnosisCode().equals(host.diagnosis()) && c.procedureCodes().equals(host.procedures());
            }).toList();
            long natural = rows.stream().filter(g -> g.fraudPattern().equals("none")
                    || g.scenario().startsWith("near_miss_band")).count();
            long planted = rows.stream().filter(g -> g.scenario().equals("band_amount")
                    || g.scenario().equals("band_noise")).count();
            assertEquals(32, natural, host.procedures() + ": natural quota incl. the band near misses");
            assertEquals(10, planted, host.procedures() + ": 5 fraud + 5 noise");
            assertEquals(42, rows.size());
            assertTrue((double) planted / rows.size() <= 0.25, host.procedures() + ": " + planted + "/" + rows.size());
        }
        for (Claim c : ofScenario("band_noise")) {
            assertTrue(c.submittedAmount() >= 47_500 && c.submittedAmount() <= 49_999);
        }
        for (Claim c : ofScenario("near_miss_band_below")) {
            assertTrue(c.submittedAmount() >= 47_000 && c.submittedAmount() <= 47_499, c.claimId());
        }
        for (Claim c : ofScenario("near_miss_band_above")) {
            assertTrue(c.submittedAmount() >= 50_000 && c.submittedAmount() <= 50_500, c.claimId());
        }
    }

    @Test
    void phantomDayHasThirtyOneClaimsAndTheThirtyClaimDayExactlyThirty() {
        assertEquals(31, onDay(dataset.phantomDay()).size());
        List<Claim> day30 = onDay(dataset.day30());
        assertEquals(30, day30.size());
        assertTrue(day30.stream().allMatch(c -> truth.get(c.claimId()).fraudPattern().equals("legit_near_miss")
                && truth.get(c.claimId()).scenario().equals("day_30_claims")));
        assertEquals(30, day30.stream().map(Claim::memberId).distinct().count());
    }

    private static List<Claim> onDay(ScenarioDay day) {
        return dataset.claims().stream()
                .filter(c -> c.providerId().equals(day.providerId()) && c.claimDate().equals(day.date())).toList();
    }

    @Test
    void eightDayPairsAndSameDayPairsPointToTheirPartner() {
        for (Claim c : ofScenario("near_miss_resubmission_8_days")) {
            Claim source = claims.get(truth.get(c.claimId()).relatedClaimId());
            assertEquals(8, ChronoUnit.DAYS.between(source.claimDate(), c.claimDate()), c.claimId());
            assertEquals(source.memberId(), c.memberId());
            assertEquals(source.diagnosisCode(), c.diagnosisCode());
        }
        for (Claim c : ofScenario("near_miss_same_day_other_diagnosis")) {
            Claim source = claims.get(truth.get(c.claimId()).relatedClaimId());
            assertEquals(List.of(source.memberId(), source.providerId(), source.claimDate()),
                    List.of(c.memberId(), c.providerId(), c.claimDate()));
            assertFalse(source.diagnosisCode().equals(c.diagnosisCode()), "a different diagnosis: not a duplicate");
        }
    }

    @Test
    void weekendSurgeriesAtRegularHospitalsPartialBundlesAndMildAmountsAreShapedAsSpecified() {
        for (Claim c : ofScenario("near_miss_weekend_surgery")) {
            assertEquals(Tier.REGULAR_HOSPITAL, providers.get(c.providerId()).tier());
            assertTrue(c.isWeekend());
            assertTrue(c.procedureCodes().stream().anyMatch(code -> catalog.procedure(code).orElseThrow().surgical()));
            List<Claim> others = dataset.claims().stream()
                    .filter(o -> o.providerId().equals(c.providerId()) && !o.claimId().equals(c.claimId())).toList();
            assertTrue((double) others.stream().filter(Claim::isWeekend).count() / others.size() >= 0.05,
                    c.providerId() + " has a low weekend share");
        }
        for (Claim c : ofScenario("near_miss_partial_bundle")) {
            Set<String> codes = new HashSet<>(c.procedureCodes());
            boolean partOfSome = false;
            for (Bundle b : catalog.bundles()) {
                assertFalse(codes.containsAll(b.components()) || codes.contains(b.bundleCode()), c.claimId());
                partOfSome |= b.components().stream().anyMatch(codes::contains);
            }
            assertTrue(partOfSome, c.claimId() + " shares no component with any bundle");
        }
        for (Claim c : ofScenario("near_miss_mild_amount")) {
            assertEquals(1, c.procedureCodes().size());
            double factor = (double) c.submittedAmount() / basePrice(c);
            assertTrue(factor >= 1.25 - 0.005 && factor <= 1.28 + 0.005, c.claimId() + " factor " + factor);
        }
    }

    /**
     * Every claim the generator placed after the ordinary base keeps the R2 window free of other claims of the same
     * member and diagnosis, except the intended partners (duplicate, rapid re-submission).
     */
    @Test
    void placedClaimsHaveNoUnintendedNeighbours() {
        Set<String> placedScenarios = Set.of("band_noise", "phantom_day_legit", "near_miss_band_below",
                "near_miss_band_above", "near_miss_resubmission_8_days", "near_miss_same_day_other_diagnosis",
                "near_miss_weekend_surgery", "near_miss_partial_bundle", "near_miss_mild_amount", "day_30_claims",
                "phantom_day", "weekend_surgery", "mismatch", "band_amount", "upcode_strong", "upcode_mild",
                "unbundle_full", "unbundle_double_billing");
        for (GroundTruthRow row : dataset.groundTruth()) {
            if (!placedScenarios.contains(row.scenario())) {
                continue;
            }
            Claim c = claims.get(row.claimId());
            List<String> neighbours = dataset.claims().stream()
                    .filter(o -> !o.claimId().equals(c.claimId()) && o.memberId().equals(c.memberId())
                            && o.diagnosisCode().equals(c.diagnosisCode())
                            && Math.abs(ChronoUnit.DAYS.between(o.claimDate(), c.claimDate())) <= 7)
                    .map(Claim::claimId).toList();
            assertEquals(List.of(), neighbours, row.claimId() + " (" + row.scenario() + ")");
        }
    }

    @Test
    void finalDatasetRereadsCleanly() {
        LoadResult loaded = new ClaimCsvReader(catalog).read(ClaimCsvWriter.toBytes(dataset.claims()));
        assertEquals(List.of(), loaded.rejected());
        assertEquals(List.of(), loaded.warnings());
        assertEquals(2_000, loaded.claims().size());
    }
}
