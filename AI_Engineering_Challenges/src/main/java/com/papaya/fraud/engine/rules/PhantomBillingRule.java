package com.papaya.fraud.engine.rules;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.Rule;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;

/**
 * R6: when a provider has more than {@code max_claims_per_day} claims on one date (strict, R10), every claim of
 * that provider-day is flagged, including any genuine ones (D10-B).
 */
public final class PhantomBillingRule implements Rule {

    private final int severity;
    private final int maxClaimsPerDay;

    public PhantomBillingRule(FraudConfig config) {
        this.severity = config.weight(RuleId.PHANTOM_BILLING);
        this.maxClaimsPerDay = config.params().phantomBilling().maxClaimsPerDay();
    }

    @Override
    public RuleId id() {
        return RuleId.PHANTOM_BILLING;
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        int count = index.countOnProviderDay(claim.providerId(), claim.claimDate());
        if (count <= maxClaimsPerDay) {
            return Optional.empty();
        }
        String evidence = Evidence.format(
                "Provider %s submitted %d claims on %s, more than the maximum of %d claims per day;"
                        + " every claim of that provider-day is flagged",
                claim.providerId(), count, claim.claimDate(), maxClaimsPerDay);
        return Optional.of(new Flag(id(), severity, evidence));
    }
}
