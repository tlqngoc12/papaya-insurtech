package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.MONDAY;
import static com.papaya.fraud.testing.ClaimBuilder.SATURDAY;
import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.rules.WeekendAnomalyRule.Coverage;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.testing.ClaimBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeekendAnomalyRuleTest {

    private final FraudConfig config = ConfigLoader.loadDefault();
    private final Catalog catalog = CatalogLoader.loadDefault();
    private final WeekendAnomalyRule rule = new WeekendAnomalyRule(config, catalog);

    /** An appendectomy package (surgical) billed on Saturday 2024-03-16 at PRV-01. */
    private static ClaimBuilder saturdaySurgery(String id) {
        return claim(id).withType(ClaimType.INPATIENT).withDiagnosis("K35.8").withProcedures("P-2101")
                .withAmount(30_000).withDate(SATURDAY);
    }

    /** The claim under test plus {@code others} other claims of PRV-01, {@code weekendOthers} of them on Saturdays. */
    private static List<Claim> provider(Claim underTest, int others, int weekendOthers) {
        List<Claim> claims = new ArrayList<>(List.of(underTest));
        for (int i = 0; i < others; i++) {
            boolean weekend = i < weekendOthers;
            claims.add(claim("O-" + i).withMember("M-" + i)
                    .withDate(weekend ? SATURDAY.plusWeeks(i + 1) : MONDAY.plusWeeks(i)).build());
        }
        return claims;
    }

    private boolean flagged(Claim c, List<Claim> all) {
        return rule.evaluate(c, indexOf(all)).isPresent();
    }

    @Test
    void saturdaySurgeryBelowFivePercentIsFlaggedWithEvidence() {
        // 40 claims at PRV-01, two of them weekend surgeries: each sees 1 weekend claim among 39 others.
        Claim we1 = saturdaySurgery("WE1").build();
        List<Claim> claims = provider(we1, 38, 0);
        Claim we2 = saturdaySurgery("WE2").withMember("M-WE2").withDate(SATURDAY.plusWeeks(5)).build();
        claims.add(we2);
        DatasetIndex index = indexOf(claims);

        Flag flag = rule.evaluate(we1, index).orElseThrow();
        assertEquals(RuleId.WEEKEND_ANOMALY, flag.rule());
        assertEquals(3, flag.severity());
        assertEquals("Surgical procedure P-2101 (Appendectomy package) on Saturday 2024-03-16 at provider PRV-01,"
                + " which has 1 weekend claim out of 39 other claims (2.5%), below the 5.0% weekend threshold",
                flag.evidence());
        assertTrue(rule.evaluate(we2, index).isPresent(), "the second weekend surgery sees 1 of 39 as well");
    }

    @ParameterizedTest(name = "{1} weekend of {0} other claims -> flagged: {2}")
    @CsvSource({
        "20, 1, false",   // exactly 5%: R10, < is strict
        "20, 0, true",    // 0%
        "19, 0, false",   // history 19 < min_history 20
        "40, 12, false"   // a provider that often works weekends (30%)
    })
    void ratioAndHistoryBoundaries(int others, int weekendOthers, boolean expected) {
        Claim surgery = saturdaySurgery("S").build();
        assertEquals(expected, flagged(surgery, provider(surgery, others, weekendOthers)));
    }

    @Test
    void sundayCountsAsWeekendAndEvidenceSaysSo() {
        Claim sunday = saturdaySurgery("S").withDate(SATURDAY.plusDays(1)).build();
        Flag flag = rule.evaluate(sunday, indexOf(provider(sunday, 20, 0))).orElseThrow();
        assertTrue(flag.evidence().contains("on Sunday 2024-03-17"), flag.evidence());
        assertTrue(flag.evidence().contains("0 weekend claims out of 20 other claims (0.0%)"), flag.evidence());
    }

    @Test
    void nonSurgicalWeekendClaimAndWeekdaySurgeryAreNotFlagged() {
        Claim wardStay = saturdaySurgery("S1").withProcedures("P-2001").build();
        assertTrue(!flagged(wardStay, provider(wardStay, 30, 0)), "P-2001 is not surgical");

        Claim weekdaySurgery = saturdaySurgery("S2").withDate(MONDAY).build();
        assertTrue(!flagged(weekdaySurgery, provider(weekdaySurgery, 30, 0)));

        Claim unknownCode = saturdaySurgery("S3").withProcedures("P-9999").build();
        assertTrue(!flagged(unknownCode, provider(unknownCode, 30, 0)), "codes missing from the catalog are not surgical");
    }

    @Test
    void coverageCountsJudgedAndShortHistoryWeekendSurgeries() {
        List<Claim> claims = provider(saturdaySurgery("S1").build(), 20, 0);
        claims.add(saturdaySurgery("SOLO").withProvider("PRV-SOLO").build());
        claims.add(saturdaySurgery("WARD").withMember("M-W").withProcedures("P-2001").build());
        assertEquals(new Coverage(1, 1), rule.coverage(indexOf(claims)));
    }

    @Test
    void weekendSurgeryThatIsAlsoUpcodedGetsBothFlags() {
        // 40 P-2101 claims at 30,000 except the Saturday one at 60,000: mean 30,750, SD 4,743.4, z = 6.17.
        Claim upcoded = saturdaySurgery("WE1").withAmount(60_000).build();
        List<Claim> claims = new ArrayList<>(List.of(upcoded));
        for (int i = 0; i < 39; i++) {
            claims.add(saturdaySurgery("O-" + i).withMember("M-" + i)
                    .withDate(i == 0 ? SATURDAY.plusWeeks(1) : MONDAY.plusWeeks(i)).build());
        }
        DatasetIndex index = indexOf(claims);

        Flag weekend = rule.evaluate(upcoded, index).orElseThrow();
        Flag upcoding = new UpcodingRule(config).evaluate(upcoded, index).orElseThrow();
        assertEquals(3 + 4, weekend.severity() + upcoding.severity(), "S1-A: round(100 x 7 / 28) = 25");
        assertTrue(upcoding.evidence().startsWith("Submitted amount 60,000 for procedure P-2101 is 6.17 standard"
                + " deviations above the mean of 30,750.0 (SD 4,743.4, n = 40"), upcoding.evidence());
    }
}
