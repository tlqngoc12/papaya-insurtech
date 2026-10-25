package com.papaya.fraud.generator;

/**
 * One row of {@code ground_truth.csv} (D3 with G5). {@code fraudPattern} is one of the eight rule ids, or
 * {@code none}, {@code legit_near_miss}, {@code legit_noise} (Q7). {@code relatedClaimId} may be empty.
 */
public record GroundTruthRow(String claimId, boolean isFraud, String fraudPattern, String relatedClaimId,
                             String scenario) {
}
