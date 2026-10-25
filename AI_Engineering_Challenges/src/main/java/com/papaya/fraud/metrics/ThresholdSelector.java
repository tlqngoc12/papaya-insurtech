package com.papaya.fraud.metrics;

import java.util.List;
import java.util.Optional;

/**
 * S3 (with Q5): among the thresholds whose standard FPR is at most the limit (20%), the one with the highest recall;
 * ties go to the lowest FPR, then to the largest T. Compared on counts, so there is no floating-point tie noise.
 */
public final class ThresholdSelector {

    public static final double MAX_FPR = 0.20;

    /** One threshold of the sweep (T = 1..100) and its confusion matrix. */
    public record SweepRow(int threshold, ConfusionMatrix confusion) {
    }

    private ThresholdSelector() {
    }

    public static Optional<SweepRow> select(List<SweepRow> sweep, double maxFpr) {
        SweepRow best = null;
        for (SweepRow row : sweep) {
            if (!row.confusion().fprAtMost(maxFpr)) {
                continue;
            }
            if (best == null || better(row, best)) {
                best = row;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean better(SweepRow candidate, SweepRow best) {
        ConfusionMatrix c = candidate.confusion();
        ConfusionMatrix b = best.confusion();
        if (c.tp() != b.tp()) {
            return c.tp() > b.tp(); // higher recall (same number of fraud claims in every row)
        }
        if (c.fp() != b.fp()) {
            return c.fp() < b.fp(); // lower FPR (same number of legit claims)
        }
        return candidate.threshold() > best.threshold();
    }
}
