package com.papaya.fraud.model;

import java.util.List;

/**
 * A claim with its composite risk score (0-100) and its flags, at most one per rule, in rule order.
 */
public record ScoredClaim(Claim claim, int riskScore, List<Flag> flags) {

    public ScoredClaim {
        flags = List.copyOf(flags);
    }
}
