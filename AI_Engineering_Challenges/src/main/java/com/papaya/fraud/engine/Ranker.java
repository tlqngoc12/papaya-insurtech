package com.papaya.fraud.engine;

import com.papaya.fraud.model.ScoredClaim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * S5: risk score descending, then number of flags descending, then submitted amount descending (larger claims
 * are investigated first), then claim_id ascending. Total order, so the output is deterministic.
 */
public final class Ranker {

    public static final Comparator<ScoredClaim> ORDER = Comparator
            .comparingInt(ScoredClaim::riskScore).reversed()
            .thenComparing(Comparator.comparingInt((ScoredClaim s) -> s.flags().size()).reversed())
            .thenComparing(Comparator.comparingLong((ScoredClaim s) -> s.claim().submittedAmount()).reversed())
            .thenComparing(s -> s.claim().claimId());

    private Ranker() {
    }

    public static List<ScoredClaim> rank(List<ScoredClaim> scored) {
        List<ScoredClaim> ranked = new ArrayList<>(scored);
        ranked.sort(ORDER);
        return List.copyOf(ranked);
    }
}
