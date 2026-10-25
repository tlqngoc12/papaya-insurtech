package com.papaya.fraud.engine.rules;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.config.RuleParams;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;

/**
 * R9: flagged when lower_bound &lt;= amount &lt;= upper_bound (both inclusive, R10), i.e. just below the
 * auto-approval threshold.
 */
public final class AmountClusteringRule implements Rule {

    private final int severity;
    private final RuleParams.Clustering band;

    public AmountClusteringRule(FraudConfig config) {
        this.severity = config.weight(RuleId.AMOUNT_CLUSTERING);
        this.band = config.params().amountClustering();
    }

    @Override
    public RuleId id() {
        return RuleId.AMOUNT_CLUSTERING;
    }

    /**
     * Share of the threshold with one decimal, truncated rather than rounded so that an amount below the
     * threshold never reads as 100.0% (49,999 of 50,000 is 99.9%).
     */
    static String percentOfThreshold(long amount, long threshold) {
        // Truncated, not rounded, so the evidence never shows 100% for an amount below the threshold.
        return Evidence.percent(amount, threshold);
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        long amount = claim.submittedAmount();
        if (amount < band.lowerBound() || amount > band.upperBound()) {
            return Optional.empty();
        }
        String evidence = Evidence.format(
                "Amount %s is %s%% of the %s auto-approval threshold (clustering band %s-%s)",
                Evidence.amount(amount), percentOfThreshold(amount, band.autoApprovalThreshold()),
                Evidence.amount(band.autoApprovalThreshold()), Evidence.amount(band.lowerBound()),
                Evidence.amount(band.upperBound()));
        return Optional.of(new Flag(id(), severity, evidence));
    }
}
