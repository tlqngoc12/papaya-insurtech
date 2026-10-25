package com.papaya.fraud.engine.rules;

import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;

/**
 * A detection rule. Dataset-wide facts come from the {@link DatasetIndex}; a rule never recomputes them.
 * At most one flag per claim (S6). Sealed to exactly the eight rules of the challenge; it lives in the same
 * package as they do because a sealed type in the unnamed module cannot permit subclasses of another package.
 */
public sealed interface Rule permits DuplicateClaimRule, RapidResubmissionRule, UpcodingRule, UnbundlingRule,
        PhantomBillingRule, WeekendAnomalyRule, DiagnosisProcedureMismatchRule, AmountClusteringRule {

    RuleId id();

    Optional<Flag> evaluate(Claim claim, DatasetIndex index);
}
