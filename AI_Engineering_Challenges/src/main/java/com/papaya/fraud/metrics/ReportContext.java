package com.papaya.fraud.metrics;

/**
 * Provenance and timing printed with the metrics: which claims and config produced them (file name and SHA-256, never
 * a path), the seed if known, and how long loading, scoring and writing scored_claims.json took.
 */
public record ReportContext(String seed, String claimsFileName, String claimsSha256, String configFileName,
                            String configSha256, long loadMs, long scoreMs, long writeMs, String limitationsLink) {
}
