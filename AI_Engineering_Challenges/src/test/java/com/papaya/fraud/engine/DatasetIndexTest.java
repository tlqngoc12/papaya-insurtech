package com.papaya.fraud.engine;

import static com.papaya.fraud.testing.ClaimBuilder.MONDAY;
import static com.papaya.fraud.testing.ClaimBuilder.SATURDAY;
import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.engine.DatasetIndex.AmountStats;
import com.papaya.fraud.engine.DatasetIndex.ProviderVolume;
import com.papaya.fraud.model.Claim;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DatasetIndexTest {

    private static List<String> ids(List<Claim> claims) {
        return claims.stream().map(Claim::claimId).toList();
    }

    @Test
    void claimsAreOrderedByDateThenClaimId() {
        DatasetIndex index = indexOf(
                claim("C3").withDate("2024-03-12").build(),
                claim("C2").withDate("2024-03-11").build(),
                claim("C1").withDate("2024-03-12").build());

        assertEquals(List.of("C2", "C1", "C3"), ids(index.claimsInOrder()));
    }

    @Test
    void memberDiagnosisGroupsAreInSubmissionOrderAndExposeEarlierClaims() {
        Claim c1 = claim("C1").withDate("2024-03-15").build();
        Claim c2 = claim("C2").withDate("2024-03-11").build();
        Claim c3 = claim("C3").withDate("2024-03-11").build();
        Claim otherDx = claim("C4").withDiagnosis("J02.9").build();
        Claim otherMember = claim("C5").withMember("M-0002").build();
        DatasetIndex index = indexOf(c1, otherMember, c3, otherDx, c2);

        assertEquals(List.of("C2", "C3", "C1"), ids(index.sameMemberAndDiagnosis(c1)));
        assertEquals(List.of("C2", "C3"), ids(index.earlierSameMemberAndDiagnosis(c1)));
        assertEquals(List.of("C2"), ids(index.earlierSameMemberAndDiagnosis(c3)));
        assertEquals(List.of(), ids(index.earlierSameMemberAndDiagnosis(c2)));
        assertEquals(List.of("C4"), ids(index.sameMemberAndDiagnosis(otherDx)));
        assertEquals(3, index.memberDiagnosisGroups().size());
    }

    @Test
    void countsClaimsPerProviderDay() {
        DatasetIndex index = indexOf(
                claim("C1").build(),
                claim("C2").withMember("M-2").build(),
                claim("C3").withMember("M-3").build(),
                claim("C4").withDate(MONDAY.plusDays(1)).build(),
                claim("C5").withProvider("PRV-02").build());

        assertEquals(3, index.countOnProviderDay("PRV-01", MONDAY));
        assertEquals(List.of("C1", "C2", "C3"), ids(index.claimsOnProviderDay("PRV-01", MONDAY)));
        assertEquals(1, index.countOnProviderDay("PRV-01", MONDAY.plusDays(1)));
        assertEquals(1, index.countOnProviderDay("PRV-02", MONDAY));
        assertEquals(0, index.countOnProviderDay("PRV-99", MONDAY));
    }

    @Test
    void signatureStatsUseSampleStandardDeviation() {
        DatasetIndex index = indexOf(
                claim("C1").withProcedures("P-1001", "P-1002").withAmount(300).build(),
                claim("C2").withProcedures("P-1002", "P-1001").withAmount(100).build(),
                claim("C3").withProcedures("P-1001", "P-1002", "P-1001").withAmount(200).build(),
                claim("C4").withProcedures("P-1005").withAmount(700).build());

        assertEquals("P-1001|P-1002", DatasetIndex.signature(index.claim("C2").orElseThrow()),
                "signature ignores submission order and repeats");
        AmountStats s = index.signatureStats("P-1001|P-1002").orElseThrow();
        assertEquals(3, s.n());
        assertEquals(200.0, s.mean(), 1e-9);
        assertEquals(100.0, s.sampleSd(), 1e-9, "sqrt((100^2 + 0 + 100^2) / (3 - 1)) = 100");
        assertEquals(List.of(100L, 200L, 300L), s.sortedAmounts());

        AmountStats single = index.signatureStats("P-1005").orElseThrow();
        assertEquals(1, single.n());
        assertTrue(Double.isNaN(single.sampleSd()), "SD is undefined for one claim");
        assertTrue(index.signatureStats("P-9999").isEmpty());
    }

    @Test
    void weekendRatioLeavesTheGivenClaimOut() {
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < 38; i++) {
            claims.add(claim("WD" + i).withMember("M-" + i).withDate(MONDAY.plusWeeks(i % 30).plusDays(i / 30)).build());
        }
        Claim sat1 = claim("WE1").withDate(SATURDAY).build();
        Claim sat2 = claim("WE2").withDate(SATURDAY.plusWeeks(1)).build();
        claims.add(sat1);
        claims.add(sat2);
        Claim lonely = claim("L1").withProvider("PRV-SOLO").build();
        claims.add(lonely);
        DatasetIndex index = indexOf(claims);

        assertEquals(new ProviderVolume(40, 2), index.providerVolume("PRV-01"));
        assertEquals(39, index.historyExcluding(sat1));
        assertEquals(1.0 / 39, index.weekendRatioExcluding(sat1).getAsDouble(), 1e-12, "2 - 1 weekend of 39");
        assertEquals(2.0 / 39, index.weekendRatioExcluding(claims.get(0)).getAsDouble(), 1e-12, "2 weekend of 39");
        assertTrue(index.weekendRatioExcluding(lonely).isEmpty(), "no other claim at the provider");
        assertEquals(0, index.historyExcluding(lonely));
        assertEquals(new ProviderVolume(0, 0), index.providerVolume("PRV-99"));
        assertThrows(IllegalArgumentException.class,
                () -> index.weekendRatioExcluding(claim("X").build()), "claim must belong to the index");
    }

    @Test
    void emptyDatasetHasNothingToLookUp() {
        DatasetIndex index = DatasetIndex.of(List.of());
        Claim c = claim("C1").build();

        assertEquals(0, index.size());
        assertEquals(List.of(), index.claimsInOrder());
        assertEquals(List.of(), index.sameMemberAndDiagnosis(c));
        assertEquals(List.of(), index.earlierSameMemberAndDiagnosis(c));
        assertEquals(0, index.countOnProviderDay("PRV-01", MONDAY));
        assertTrue(index.signatureStats("P-1001").isEmpty());
        assertEquals(new ProviderVolume(0, 0), index.providerVolume("PRV-01"));
    }

    @Test
    void shuffledInputBuildsAnIdenticalIndex() {
        List<Claim> claims = randomClaims(500, new Random(7));
        DatasetIndex reference = DatasetIndex.of(claims);
        for (long seed : new long[] {1, 2, 3}) {
            List<Claim> shuffled = new ArrayList<>(claims);
            Collections.shuffle(shuffled, new Random(seed));
            DatasetIndex other = DatasetIndex.of(shuffled);

            assertEquals(reference.claimsInOrder(), other.claimsInOrder());
            assertEquals(reference.memberDiagnosisGroups(), other.memberDiagnosisGroups());
            assertEquals(reference.providerDays(), other.providerDays());
            assertEquals(reference.signatureGroups(), other.signatureGroups(),
                    "stats are computed from sorted amounts, so even floating-point results match");
            for (Claim c : claims) {
                assertEquals(reference.weekendRatioExcluding(c), other.weekendRatioExcluding(c));
            }
        }
    }

    @Test
    void indexCannotBeModifiedFromOutside() {
        Claim c1 = claim("C1").build();
        List<Claim> input = new ArrayList<>(List.of(c1, claim("C2").withDate("2024-03-12").build()));
        DatasetIndex index = DatasetIndex.of(input);
        input.clear();

        assertEquals(2, index.size(), "later changes to the input list do not reach the index");
        assertThrows(UnsupportedOperationException.class, () -> index.claimsInOrder().add(c1));
        assertThrows(UnsupportedOperationException.class, () -> index.sameMemberAndDiagnosis(c1).add(c1));
        assertThrows(UnsupportedOperationException.class, () -> index.earlierSameMemberAndDiagnosis(c1).clear());
        assertThrows(UnsupportedOperationException.class, () -> index.claimsOnProviderDay("PRV-01", MONDAY).clear());
        assertThrows(UnsupportedOperationException.class, () -> index.providerDays().clear());
        assertThrows(UnsupportedOperationException.class, () -> index.memberDiagnosisGroups().clear());
        assertThrows(UnsupportedOperationException.class, () -> index.signatureGroups().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> index.signatureStats("P-1001").orElseThrow().sortedAmounts().add(1L));
    }

    @Test
    void duplicateClaimIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> indexOf(claim("C1").build(), claim("C1").withDate("2024-04-01").build()));
    }

    @Test
    void buildsOneHundredThousandClaimsQuickly() {
        List<Claim> claims = randomClaims(100_000, new Random(42));
        DatasetIndex index = assertTimeout(Duration.ofSeconds(30), () -> {
            long start = System.nanoTime();
            DatasetIndex built = DatasetIndex.of(claims);
            System.out.printf("DatasetIndex: built 100,000 claims in %d ms%n",
                    (System.nanoTime() - start) / 1_000_000);
            return built;
        });
        assertEquals(100_000, index.size());
        assertFalse(index.providerDays().isEmpty());
    }

    private static List<Claim> randomClaims(int n, Random random) {
        String[] diagnoses = {"J06.9", "J02.9", "I10", "E11.9"};
        String[][] procedures = {{"P-1001"}, {"P-1002"}, {"P-1001", "P-1002"}, {"P-1005"}};
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<Claim> claims = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            claims.add(claim(String.format("CLM-%06d", i))
                    .withMember("M-" + random.nextInt(500))
                    .withProvider("PRV-" + random.nextInt(50))
                    .withDate(start.plusDays(random.nextInt(366)))
                    .withDiagnosis(diagnoses[random.nextInt(diagnoses.length)])
                    .withProcedures(procedures[random.nextInt(procedures.length)])
                    .withAmount(100 + random.nextInt(10_000))
                    .build());
        }
        return claims;
    }
}
