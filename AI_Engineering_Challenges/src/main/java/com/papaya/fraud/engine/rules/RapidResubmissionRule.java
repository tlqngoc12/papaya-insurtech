package com.papaya.fraud.engine.rules;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.Rule;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * R2: flagged when at least one earlier claim (D14 order) of the same member and diagnosis lies within
 * {@code window_days} (inclusive, R10), at any provider. Pairs that are already duplicates of this claim (same
 * provider and date) are not counted, so one behaviour is not scored twice. The first claim of a chain is never
 * flagged.
 */
public final class RapidResubmissionRule implements Rule {

    private final int severity;
    private final int windowDays;

    public RapidResubmissionRule(FraudConfig config) {
        this.severity = config.weight(RuleId.RAPID_RESUBMISSION);
        this.windowDays = config.params().rapidResubmission().windowDays();
    }

    @Override
    public RuleId id() {
        return RuleId.RAPID_RESUBMISSION;
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        List<Claim> inWindow = index.earlierSameMemberAndDiagnosis(claim).stream()
                .filter(e -> !DuplicateClaimRule.isDuplicatePair(e, claim))
                .filter(e -> ChronoUnit.DAYS.between(e.claimDate(), claim.claimDate()) <= windowDays)
                .toList();
        if (inWindow.isEmpty()) {
            return Optional.empty();
        }
        // Earlier claims are in submission order, so the last one is the closest.
        Claim previous = inWindow.get(inWindow.size() - 1);
        long days = ChronoUnit.DAYS.between(previous.claimDate(), claim.claimDate());
        String evidence = Evidence.format(
                "Re-submitted %d %s after %s (%s, provider %s) for the same member %s and diagnosis %s;"
                        + " %d earlier %s within the %d-day window",
                days, days == 1 ? "day" : "days", previous.claimId(), previous.claimDate(), previous.providerId(),
                claim.memberId(), claim.diagnosisCode(), inWindow.size(), inWindow.size() == 1 ? "claim" : "claims",
                windowDays);
        return Optional.of(new Flag(id(), severity, evidence));
    }
}
