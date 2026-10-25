package com.papaya.fraud.engine;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.rules.AmountClusteringRule;
import com.papaya.fraud.engine.rules.DiagnosisProcedureMismatchRule;
import com.papaya.fraud.engine.rules.DuplicateClaimRule;
import com.papaya.fraud.engine.rules.PhantomBillingRule;
import com.papaya.fraud.engine.rules.RapidResubmissionRule;
import com.papaya.fraud.engine.rules.Rule;
import com.papaya.fraud.engine.rules.UnbundlingRule;
import com.papaya.fraud.engine.rules.UpcodingRule;
import com.papaya.fraud.engine.rules.WeekendAnomalyRule;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Two-phase scoring (T1): build the {@link DatasetIndex} once, then run the eight rules on every claim, score
 * (S1-A) and rank (S5). Never sees ground truth.
 */
public final class FraudEngine {

    /** The ranked claims and the index they were scored against (needed for coverage reports). */
    public record Result(DatasetIndex index, List<ScoredClaim> ranked) {
    }

    private final List<Rule> rules;
    private final ScoreCalculator calculator;

    public FraudEngine(FraudConfig config, Catalog catalog) {
        // In RuleId order, so the flags of a claim always come out in that order.
        this.rules = List.of(
                new DuplicateClaimRule(config),
                new RapidResubmissionRule(config),
                new UpcodingRule(config),
                new UnbundlingRule(config, catalog),
                new PhantomBillingRule(config),
                new WeekendAnomalyRule(config, catalog),
                new DiagnosisProcedureMismatchRule(config, catalog),
                new AmountClusteringRule(config));
        this.calculator = new ScoreCalculator(config);
    }

    public List<Rule> rules() {
        return rules;
    }

    public Result run(List<Claim> claims) {
        DatasetIndex index = DatasetIndex.of(claims);
        List<ScoredClaim> scored = new ArrayList<>(index.size());
        for (Claim claim : index.claimsInOrder()) {
            List<Flag> flags = new ArrayList<>();
            for (Rule rule : rules) {
                rule.evaluate(claim, index).ifPresent(flags::add);
            }
            scored.add(new ScoredClaim(claim, calculator.score(flags), flags));
        }
        return new Result(index, Ranker.rank(scored));
    }

    /** Coverage of the rules that skip some claims; the switch is exhaustive because {@link Rule} is sealed. */
    public Map<RuleId, Record> coverage(DatasetIndex index) {
        Map<RuleId, Record> coverage = new EnumMap<>(RuleId.class);
        for (Rule rule : rules) {
            Optional<Record> c = switch (rule) {
                case UpcodingRule r -> Optional.of(r.coverage(index));
                case UnbundlingRule r -> Optional.of(r.coverage(index));
                case WeekendAnomalyRule r -> Optional.of(r.coverage(index));
                case DiagnosisProcedureMismatchRule r -> Optional.of(r.coverage(index));
                case DuplicateClaimRule r -> Optional.empty();
                case RapidResubmissionRule r -> Optional.empty();
                case PhantomBillingRule r -> Optional.empty();
                case AmountClusteringRule r -> Optional.empty();
            };
            c.ifPresent(value -> coverage.put(rule.id(), value));
        }
        return coverage;
    }
}
