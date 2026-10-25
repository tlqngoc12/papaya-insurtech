package com.papaya.fraud.metrics;

/**
 * Provenance and timing printed with the metrics: which claims and config produced them (SHA-256), the seed if known,
 * and how long loading, scoring and writing scored_claims.json took.
 */
public record ReportContext(String seed, String claimsFile, String claimsSha256, String configFile,
                            String configSha256, long loadMs, long scoreMs, long writeMs, String limitationsLink) {
}
