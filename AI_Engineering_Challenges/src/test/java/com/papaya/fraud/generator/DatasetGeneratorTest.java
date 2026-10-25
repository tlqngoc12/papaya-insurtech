package com.papaya.fraud.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Bundle;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.generator.GeneratedDataset.ScenarioDay;
import com.papaya.fraud.generator.ProviderProfile.Tier;
import com.papaya.fraud.io.ClaimCsv;
import com.papaya.fraud.io.ClaimCsvReader;
import com.papaya.fraud.io.ClaimCsvWriter;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Properties of the ordinary legit claims ({@code none}) of the dataset generated with the tuning seed (42); the
 * fraud patterns are checked in {@link GeneratorFraudPatternsTest}.
 */
class DatasetGeneratorTest {

    private static final long SEED = 42;
    private static GeneratorConfig config;
    private static Catalog catalog;
    private static GeneratedDataset dataset;
    private static Map<String, Claim> byId;
    private static Map<String, ProviderProfile> providers;
    private static List<Claim> ordinary;

    @BeforeAll
    static void generate() {
        config = GeneratorConfigLoader.loadDefault();
        catalog = CatalogLoader.loadDefault();
        dataset = new DatasetGenerator(config, catalog).generate(SEED);
        byId = dataset.claims().stream().collect(Collectors.toMap(Claim::claimId, Function.identity()));
        providers = dataset.providers().stream().collect(Collectors.toMap(ProviderProfile::id, Function.identity()));
        Set<String> ordinaryIds = dataset.groundTruth().stream().filter(g -> g.fraudPattern().equals("none"))
                .map(GroundTruthRow::claimId).collect(Collectors.toSet());
        ordinary = dataset.claims().stream().filter(c -> ordinaryIds.contains(c.claimId())).toList();
    }

    private static <K> Map<K, Long> count(Function<Claim, K> key) {
        return dataset.claims().stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    @Test
    void producesTheBaseClaimCountAndUsesEveryMemberAndProvider() {
        assertEquals(config.baseClaims(), ordinary.size(), "1,719 ordinary legit claims");
        assertEquals(1_719, config.baseClaims());
        assertEquals(1_719 + 115, dataset.claims().size(), "plus 30 + 30 + 30 + 25 fraud claims of Task 13");
        assertEquals(500, dataset.claims().stream().map(Claim::memberId).distinct().count());
        assertEquals(50, dataset.claims().stream().map(Claim::providerId).distinct().count());
        assertEquals(50, dataset.providers().size());
    }

    @Test
    void rereadingTheCsvGivesNoRejectedRowAndNoWarning() {
        byte[] csv = ClaimCsvWriter.toBytes(dataset.claims());
        LoadResult loaded = new ClaimCsvReader(catalog).read(csv);

        assertEquals(List.of(), loaded.rejected());
        assertEquals(List.of(), loaded.warnings());
        assertEquals(dataset.claims(), loaded.claims());
    }

    @Test
    void everyClaimIsConsistentWithTheCatalog() {
        for (Claim c : dataset.claims()) {
            ProviderProfile p = providers.get(c.providerId());
            assertTrue(p.tier().claimTypes().contains(c.claimType()), c.claimId() + " type vs provider tier");
            assertTrue(catalog.diagnosis(c.diagnosisCode()).orElseThrow().claimTypes().contains(c.claimType()),
                    c.claimId() + " diagnosis vs claim type");
            for (String code : c.procedureCodes()) {
                assertTrue(catalog.isValidPair(c.diagnosisCode(), code), c.claimId() + " " + code + " vs diagnosis");
                assertTrue(catalog.procedure(code).orElseThrow().claimTypes().contains(c.claimType()),
                        c.claimId() + " " + code + " vs claim type");
            }
            assertEquals(ClaimCsv.isWeekend(c.claimDate()), c.isWeekend());
        }
    }

    @Test
    void weekdayOnlyHospitalsHaveExactlyTheQuotaAndNoWeekendClaim() {
        List<ProviderProfile> weekdayOnly = dataset.providers().stream()
                .filter(p -> p.tier() == Tier.WEEKDAY_ONLY_HOSPITAL).toList();
        assertEquals(8, weekdayOnly.size());
        for (ProviderProfile p : weekdayOnly) {
            assertEquals(65, ordinary.stream().filter(c -> c.providerId().equals(p.id())).count(), p.id());
            assertTrue(dataset.claims().stream().filter(c -> c.providerId().equals(p.id())).noneMatch(Claim::isWeekend),
                    p.id() + " has a weekend claim (no weekend-anomaly fraud yet)");
        }
    }

    @Test
    void bandHostsHoldTheirNaturalQuotaMinusTheNearMissesStillToCome() {
        Map<String, Long> bySignature = count(c -> c.diagnosisCode() + " " + String.join("+", c.procedureCodes()));
        assertEquals(30L, bySignature.get("I21.9 P-2010"));
        assertEquals(30L, bySignature.get("O82 P-2009"));
        assertEquals(31L, bySignature.get("I21.9 P-2010+P-2001"));
        assertEquals(31L, bySignature.get("O82 P-2009+P-2001"));
    }

    @Test
    void reservedScenarioDaysAreEmptyWeekdaysAtTwoDifferentClinics() {
        for (ScenarioDay day : List.of(dataset.phantomDay(), dataset.day30())) {
            assertEquals(Tier.CLINIC, providers.get(day.providerId()).tier());
            assertFalse(ClaimCsv.isWeekend(day.date()), day + " must be a weekday");
            assertTrue(dataset.claims().stream().noneMatch(c ->
                    c.providerId().equals(day.providerId()) && c.claimDate().equals(day.date())), day + " is not empty");
        }
        assertNotEquals(dataset.phantomDay().providerId(), dataset.day30().providerId());
    }

    @Test
    void legitClaimsNeverBillAFullBundleAndGenericCodesStandAlone() {
        for (Claim c : ordinary) {
            Set<String> codes = new HashSet<>(c.procedureCodes());
            for (Bundle b : catalog.bundles()) {
                assertFalse(codes.containsAll(b.components()), c.claimId() + " unbundles " + b.bundleCode());
            }
            if (codes.stream().anyMatch(catalog.genericCodes()::contains)) {
                assertEquals(1, c.procedureCodes().size(), c.claimId() + ": generic code with other procedures");
            }
        }
    }

    @Test
    void consultationOnlyInpatientClaimsAreRareAndAmountsStayInsideTheNoiseBand() {
        long inpatient = ordinary.stream().filter(c -> c.claimType() == ClaimType.INPATIENT).count();
        long consultationOnly = ordinary.stream()
                .filter(c -> c.claimType() == ClaimType.INPATIENT && c.procedureCodes().equals(List.of("P-0001")))
                .count();
        assertTrue(consultationOnly <= 0.03 * inpatient, consultationOnly + " of " + inpatient);

        for (Claim c : ordinary) {
            long base = c.procedureCodes().stream()
                    .mapToLong(code -> catalog.procedure(code).orElseThrow().basePrice()).sum();
            double m = (double) c.submittedAmount() / base;
            assertTrue(m >= 0.55 - 0.01 && m <= 1.45 + 0.01, c.claimId() + ": multiplier " + m);
        }
    }

    @Test
    void claimTypeMixIsCloseToTheConfiguredShares() {
        Map<ClaimType, Long> types = ordinary.stream()
                .collect(Collectors.groupingBy(Claim::claimType, TreeMap::new, Collectors.counting()));
        double n = ordinary.size();
        assertEquals(0.55, types.get(ClaimType.OUTPATIENT) / n, 0.04);
        assertEquals(0.25, types.get(ClaimType.INPATIENT) / n, 0.04);
        assertEquals(0.20, types.get(ClaimType.DENTAL) / n, 0.04);
    }

    @Test
    void groundTruthOfOrdinaryClaimsHasTheirScenarios() {
        assertEquals(dataset.claims().stream().map(Claim::claimId).toList(),
                dataset.groundTruth().stream().map(GroundTruthRow::claimId).toList());
        List<GroundTruthRow> rows = dataset.groundTruth().stream().filter(g -> g.fraudPattern().equals("none")).toList();
        Map<String, Long> scenarios = rows.stream()
                .collect(Collectors.groupingBy(GroundTruthRow::scenario, TreeMap::new, Collectors.counting()));
        assertEquals(Map.of("band_host", 122L, "natural_followup", 12L, "base", 1_585L), scenarios);
        for (GroundTruthRow row : rows) {
            assertFalse(row.isFraud());
            if (row.scenario().equals("natural_followup")) {
                Claim followup = byId.get(row.claimId());
                Claim source = byId.get(row.relatedClaimId());
                long gap = ChronoUnit.DAYS.between(source.claimDate(), followup.claimDate());
                assertTrue(gap >= 2 && gap <= 7, row.claimId() + " gap " + gap);
                assertEquals(source.memberId(), followup.memberId());
                assertEquals(source.diagnosisCode(), followup.diagnosisCode());
            } else {
                assertEquals("", row.relatedClaimId());
            }
        }
    }

    @Test
    void claimIdsAreSequentialInDateOrder() {
        List<Claim> claims = dataset.claims();
        for (int i = 0; i < claims.size(); i++) {
            assertEquals(String.format("CLM-%05d", i + 1), claims.get(i).claimId());
            if (i > 0) {
                assertFalse(claims.get(i).claimDate().isBefore(claims.get(i - 1).claimDate()));
            }
        }
    }

    @Test
    void chronicMembersAreAFifthWithAChronicDiagnosis() {
        long chronic = dataset.members().stream().filter(m -> m.chronicDiagnosis() != null).count();
        assertEquals(100, chronic);
        assertTrue(dataset.members().stream().map(MemberProfile::chronicDiagnosis).filter(d -> d != null)
                .allMatch(Set.of("I10", "E11.9", "M17.1")::contains));
        assertEquals(EnumSet.allOf(Tier.class), dataset.providers().stream().map(ProviderProfile::tier)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Tier.class))));
    }
}
