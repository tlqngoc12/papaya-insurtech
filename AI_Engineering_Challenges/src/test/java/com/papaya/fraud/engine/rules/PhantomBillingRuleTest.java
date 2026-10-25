package com.papaya.fraud.engine.rules;

import static com.papaya.fraud.testing.ClaimBuilder.MONDAY;
import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static com.papaya.fraud.testing.ClaimBuilder.indexOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PhantomBillingRuleTest {

    private final PhantomBillingRule rule = new PhantomBillingRule(ConfigLoader.loadDefault());

    private static List<Claim> providerDay(String provider, int count) {
        List<Claim> claims = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            claims.add(claim(provider + "-" + i).withProvider(provider).withMember("M-" + i).build());
        }
        return claims;
    }

    @ParameterizedTest(name = "{0} claims on one provider-day -> flagged: {1}")
    @CsvSource({"30, false", "31, true"})
    void flagsEveryClaimOfTheDayOnlyAboveThirty(int count, boolean flagged) {
        List<Claim> claims = providerDay("PRV-07", count);
        DatasetIndex index = indexOf(claims);

        long flags = claims.stream().filter(c -> rule.evaluate(c, index).isPresent()).count();
        assertEquals(flagged ? count : 0, flags, "R10: > 30 is strict, and it is all or nothing for the day");
    }

    @Test
    void evidenceNamesProviderDateCountAndLimit() {
        List<Claim> claims = providerDay("PRV-07", 31);
        Flag flag = rule.evaluate(claims.get(0), indexOf(claims)).orElseThrow();

        assertEquals(RuleId.PHANTOM_BILLING, flag.rule());
        assertEquals(5, flag.severity());
        assertEquals("Provider PRV-07 submitted 31 claims on 2024-03-11, more than the maximum of 30 claims per day;"
                + " every claim of that provider-day is flagged", flag.evidence());
    }

    @Test
    void otherProvidersAndOtherDaysAreNotAffected() {
        List<Claim> claims = new ArrayList<>(providerDay("PRV-07", 31));
        Claim sameDayOtherProvider = claim("X1").withProvider("PRV-08").build();
        Claim sameProviderNextDay = claim("X2").withProvider("PRV-07").withDate(MONDAY.plusDays(1)).build();
        claims.add(sameDayOtherProvider);
        claims.add(sameProviderNextDay);
        DatasetIndex index = indexOf(claims);

        assertTrue(rule.evaluate(sameDayOtherProvider, index).isEmpty());
        assertTrue(rule.evaluate(sameProviderNextDay, index).isEmpty());
        assertTrue(rule.evaluate(claims.get(30), index).isPresent());
    }
}
