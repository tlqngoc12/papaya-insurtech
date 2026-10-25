package com.papaya.fraud.engine;

import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.Optional;

/**
 * A detection rule. Dataset-wide facts come from the {@link DatasetIndex}; a rule never recomputes them.
 * At most one flag per claim (S6). To be sealed once the eight rule classes exist (Task 10).
 */
public interface Rule {

    RuleId id();

    Optional<Flag> evaluate(Claim claim, DatasetIndex index);
}
