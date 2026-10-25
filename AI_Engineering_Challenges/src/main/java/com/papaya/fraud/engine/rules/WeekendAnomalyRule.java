package com.papaya.fraud.engine.rules;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.Procedure;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.engine.DatasetIndex.ProviderVolume;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R7 (D11, Q12): a weekend claim with at least one surgical procedure, at a provider whose other claims number at
 * least {@code min_history} and whose leave-one-out weekend ratio is below {@code max_weekend_ratio} (strict, R10).
 * Codes missing from the catalog are not surgical.
 */
public final class WeekendAnomalyRule implements Rule {

    /** Weekend surgical claims judged, and those skipped because the provider has too little other history. */
    public record Coverage(int evaluated, int skippedShortHistory) {
    }

    private final int severity;
    private final double maxWeekendRatio;
    private final int minHistory;
    private final Catalog catalog;

    public WeekendAnomalyRule(FraudConfig config, Catalog catalog) {
        this.severity = config.weight(RuleId.WEEKEND_ANOMALY);
        this.maxWeekendRatio = config.params().weekendAnomaly().maxWeekendRatio();
        this.minHistory = config.params().weekendAnomaly().minHistory();
        this.catalog = catalog;
    }

    @Override
    public RuleId id() {
        return RuleId.WEEKEND_ANOMALY;
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        if (!claim.isWeekend()) {
            return Optional.empty();
        }
        List<Procedure> surgical = surgicalProcedures(claim);
        if (surgical.isEmpty() || index.historyExcluding(claim) < minHistory) {
            return Optional.empty();
        }
        double ratio = index.weekendRatioExcluding(claim).orElseThrow();
        if (!(ratio < maxWeekendRatio)) {
            return Optional.empty();
        }
        ProviderVolume others = index.volumeExcluding(claim);
        List<String> described = surgical.stream().map(p -> p.code() + " (" + p.description() + ")").toList();
        String evidence = Evidence.format(
                "Surgical %s %s on %s %s at provider %s, which has %d weekend %s out of %d other claims (%s%%),"
                        + " below the %.1f%% weekend threshold",
                surgical.size() == 1 ? "procedure" : "procedures", String.join(", ", described),
                claim.claimDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US), claim.claimDate(),
                claim.providerId(), others.weekend(), others.weekend() == 1 ? "claim" : "claims", others.total(),
                Evidence.percent(others.weekend(), others.total()), maxWeekendRatio * 100);
        return Optional.of(new Flag(id(), severity, evidence));
    }

    public Coverage coverage(DatasetIndex index) {
        int evaluated = 0;
        int shortHistory = 0;
        for (Claim c : index.claimsInOrder()) {
            if (!c.isWeekend() || surgicalProcedures(c).isEmpty()) {
                continue;
            }
            if (index.historyExcluding(c) < minHistory) {
                shortHistory++;
            } else {
                evaluated++;
            }
        }
        return new Coverage(evaluated, shortHistory);
    }

    private List<Procedure> surgicalProcedures(Claim claim) {
        return new LinkedHashSet<>(claim.procedureCodes()).stream()
                .map(catalog::procedure)
                .flatMap(Optional::stream)
                .filter(Procedure::surgical)
                .toList();
    }
}
