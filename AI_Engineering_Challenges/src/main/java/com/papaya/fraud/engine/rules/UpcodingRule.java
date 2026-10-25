package com.papaya.fraud.engine.rules;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.DatasetIndex.AmountStats;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;

/**
 * R3/R4 with Q1: only claims with exactly one procedure are checked, against all single-procedure claims of that
 * code. Mean and sample SD include the claim itself (R4-A). Flagged when z &gt; z_threshold (strict, R10).
 * Not flagged: multi-procedure claims, groups below min_group_size, SD of 0 or NaN (Q11).
 */
public final class UpcodingRule implements Rule {

    /** How many claims the rule could actually judge; reported with the metrics. */
    public record Coverage(int evaluated, int skippedMultiProcedure, int skippedSmallGroup, int skippedZeroSd) {
    }

    private enum Skip { NONE, MULTI_PROCEDURE, SMALL_GROUP, ZERO_SD }

    private final int severity;
    private final double zThreshold;
    private final int minGroupSize;

    public UpcodingRule(FraudConfig config) {
        this.severity = config.weight(RuleId.UPCODING);
        this.zThreshold = config.params().upcoding().zThreshold();
        this.minGroupSize = config.params().upcoding().minGroupSize();
    }

    @Override
    public RuleId id() {
        return RuleId.UPCODING;
    }

    static double zScore(long amount, AmountStats stats) {
        return (amount - stats.mean()) / stats.sampleSd();
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        if (skipReason(claim, index) != Skip.NONE) {
            return Optional.empty();
        }
        String code = DatasetIndex.signature(claim);
        AmountStats stats = index.signatureStats(code).orElseThrow();
        double z = zScore(claim.submittedAmount(), stats);
        if (!(z > zThreshold)) {
            return Optional.empty();
        }
        String evidence = Evidence.format(
                "Submitted amount %s for procedure %s is %.2f standard deviations above the mean of %,.1f"
                        + " (SD %,.1f, n = %d single-procedure claims of %s; threshold z > %.1f)",
                Evidence.amount(claim.submittedAmount()), code, z, stats.mean(), stats.sampleSd(), stats.n(), code,
                zThreshold);
        return Optional.of(new Flag(id(), severity, evidence));
    }

    /** Counts, over the whole dataset, how many claims were judged and why the others were skipped. */
    public Coverage coverage(DatasetIndex index) {
        int[] counts = new int[Skip.values().length];
        for (Claim c : index.claimsInOrder()) {
            counts[skipReason(c, index).ordinal()]++;
        }
        return new Coverage(counts[Skip.NONE.ordinal()], counts[Skip.MULTI_PROCEDURE.ordinal()],
                counts[Skip.SMALL_GROUP.ordinal()], counts[Skip.ZERO_SD.ordinal()]);
    }

    private Skip skipReason(Claim claim, DatasetIndex index) {
        String signature = DatasetIndex.signature(claim);
        if (signature.contains("|")) {
            return Skip.MULTI_PROCEDURE;
        }
        AmountStats stats = index.signatureStats(signature).orElseThrow();
        if (stats.n() < minGroupSize) {
            return Skip.SMALL_GROUP;
        }
        if (Double.isNaN(stats.sampleSd()) || stats.sampleSd() == 0) {
            return Skip.ZERO_SD;
        }
        return Skip.NONE;
    }
}
