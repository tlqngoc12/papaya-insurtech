package com.papaya.fraud.config;

/**
 * Tunable parameters of the rules. Defaults in {@code fraud-config.json} follow the challenge text (R10).
 */
public record RuleParams(Rapid rapidResubmission, Upcoding upcoding, Phantom phantomBilling, Weekend weekendAnomaly,
                         Clustering amountClustering) {

    /** R2: a re-submission is flagged when it follows a claim with the same member and diagnosis by at most this many days. */
    public record Rapid(int windowDays) {
    }

    /** R3/R4: flag when z &gt; zThreshold; signature groups smaller than minGroupSize are skipped. */
    public record Upcoding(double zThreshold, int minGroupSize) {
    }

    /** R6: flag every claim of a provider-day with more than this many claims. */
    public record Phantom(int maxClaimsPerDay) {
    }

    /** R7: flag surgical weekend claims of providers whose leave-one-out weekend ratio is below maxWeekendRatio. */
    public record Weekend(double maxWeekendRatio, int minHistory) {
    }

    /**
     * R9: flag amounts in [lowerBound, upperBound], both inclusive. autoApprovalThreshold is the figure quoted
     * in the evidence ("... of the 50,000 auto-approval threshold") and must lie above upperBound.
     */
    public record Clustering(long lowerBound, long upperBound, long autoApprovalThreshold) {
    }
}
