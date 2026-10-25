package com.papaya.fraud.model;

/**
 * One triggered rule on a claim. {@code severity} is the configured weight of the rule (S2); the strength of the
 * evidence is described in {@code evidence}.
 */
public record Flag(RuleId rule, int severity, String evidence) {
}
