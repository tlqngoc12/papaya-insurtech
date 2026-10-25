package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmountClusteringRuleTest {

    private final FraudConfig config = ConfigLoader.loadDefault();
    private final AmountClusteringRule rule = new AmountClusteringRule(config);

    private Optional<Flag> evaluate(long amount) {
        Claim c = claim("C1").withProcedures("P-2010").withAmount(amount).build();
        return rule.evaluate(c, indexOf(c));
    }

    @ParameterizedTest(name = "amount {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "47500 | Amount 47,500 is 95.0% of the 50,000 auto-approval threshold (clustering band 47,500-49,999)",
        "49999 | Amount 49,999 is 99.9% of the 50,000 auto-approval threshold (clustering band 47,500-49,999)",
        "47499 | NONE",
        "50000 | NONE"
    })
    void bandIsInclusiveOnBothEnds(long amount, String expectedEvidence) {
        Optional<Flag> flag = evaluate(amount);
        assertEquals(expectedEvidence, flag.map(Flag::evidence).orElse("NONE"));
    }

    @Test
    void evidenceMatchesTheChallengeExample() {
        Flag flag = evaluate(49_500).orElseThrow();
        assertEquals(RuleId.AMOUNT_CLUSTERING, flag.rule());
        assertEquals(2, flag.severity());
        assertEquals("Amount 49,500 is 99.0% of the 50,000 auto-approval threshold (clustering band 47,500-49,999)",
                flag.evidence());
    }

    @Test
    void percentageIsTruncatedNotRounded() {
        assertEquals("99.9", AmountClusteringRule.percentOfThreshold(49_999, 50_000), "not 100.0");
        assertEquals("95.0", AmountClusteringRule.percentOfThreshold(47_500, 50_000));
        assertEquals("97.3", AmountClusteringRule.percentOfThreshold(48_678, 50_000), "97.356 -> 97.3");
    }

    @Test
    void claimOnAPhantomDayInTheBandGetsBothFlags() {
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            claims.add(claim("PH-" + i).withProvider("PRV-07").withMember("M-" + i).withAmount(300).build());
        }
        Claim both = claim("PH-X").withProvider("PRV-07").withMember("M-X").withAmount(48_000).build();
        claims.add(both);
        DatasetIndex index = indexOf(claims);

        Flag phantom = new PhantomBillingRule(config).evaluate(both, index).orElseThrow();
        Flag clustering = rule.evaluate(both, index).orElseThrow();
        assertEquals(5 + 2, phantom.severity() + clustering.severity(), "S1-A: round(100 x 7 / 28) = 25");
        assertEquals("Provider PRV-07 submitted 32 claims on 2024-03-11, more than the maximum of 30 claims per day;"
                + " every claim of that provider-day is flagged", phantom.evidence());
        assertEquals("Amount 48,000 is 96.0% of the 50,000 auto-approval threshold (clustering band 47,500-49,999)",
                clustering.evidence());
    }
}
