package com.papaya.fraud.metrics;

/** One row of ground_truth.csv as the metrics module sees it. Never visible to the engine. */
record GroundTruthLabel(String claimId, boolean fraud, String pattern, String relatedClaimId, String scenario) {
}
