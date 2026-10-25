package com.papaya.fraud.engine;

import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * S1-A and S7: score = round half-up of 100 x (sum of the weights of the triggered rules) / (sum of all weights),
 * as an integer. Computed in integer arithmetic so there is no floating-point rounding at .5.
 */
public final class ScoreCalculator {

    private final FraudConfig config;
    private final int totalWeight;

    public ScoreCalculator(FraudConfig config) {
        this.config = config;
        this.totalWeight = config.totalWeight();
    }

    public int score(List<Flag> flags) {
        Set<RuleId> seen = EnumSet.noneOf(RuleId.class);
        int triggered = 0;
        for (Flag flag : flags) {
            if (!seen.add(flag.rule())) {
                throw new IllegalArgumentException("rule " + flag.rule().id() + " flagged twice on one claim (S6)");
            }
            triggered += config.weight(flag.rule());
        }
        // round(100 * t / w) half-up for t, w >= 0  ==  floor((200 * t + w) / (2 * w))
        return (200 * triggered + totalWeight) / (2 * totalWeight);
    }
}
