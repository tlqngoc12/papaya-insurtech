package com.papaya.fraud.engine.rules;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.Rule;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.List;
import java.util.Optional;

/**
 * R1: same member + provider + date + diagnosis. Every claim of such a group is flagged except the first in
 * submission order (D9, D14); the first one may be the genuine claim.
 */
public final class DuplicateClaimRule implements Rule {

    private final int severity;

    public DuplicateClaimRule(FraudConfig config) {
        this.severity = config.weight(RuleId.DUPLICATE_CLAIM);
    }

    @Override
    public RuleId id() {
        return RuleId.DUPLICATE_CLAIM;
    }

    /** Whether two claims of the same member and diagnosis also share provider and date. */
    static boolean isDuplicatePair(Claim a, Claim b) {
        return a.providerId().equals(b.providerId()) && a.claimDate().equals(b.claimDate());
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        List<Claim> earlier = index.earlierSameMemberAndDiagnosis(claim).stream()
                .filter(e -> isDuplicatePair(e, claim))
                .toList();
        if (earlier.isEmpty()) {
            return Optional.empty();
        }
        Claim original = earlier.get(0);
        long copies = index.sameMemberAndDiagnosis(claim).stream()
                .filter(c -> isDuplicatePair(c, claim))
                .count();
        String evidence = Evidence.format(
                "Duplicate of %s (occurrence %d of %d): same member %s, provider %s, date %s and diagnosis %s;"
                        + " submitted %s vs %s on the original",
                original.claimId(), earlier.size() + 1, copies, claim.memberId(), claim.providerId(),
                claim.claimDate(), claim.diagnosisCode(), Evidence.amount(claim.submittedAmount()),
                Evidence.amount(original.submittedAmount()));
        return Optional.of(new Flag(id(), severity, evidence));
    }
}
