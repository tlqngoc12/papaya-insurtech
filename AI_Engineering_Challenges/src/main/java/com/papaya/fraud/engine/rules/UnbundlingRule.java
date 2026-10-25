package com.papaya.fraud.engine.rules;

import com.papaya.fraud.catalog.Bundle;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R5: a claim matches a bundle when it bills every component of the bundle separately (unbundling), or bills the
 * bundle code together with any of its components (double billing). The diagnosis does not matter. A partial set
 * of components is not flagged. One flag per claim however many bundles match (S6).
 */
public final class UnbundlingRule implements Rule {

    /** Claims with at least two distinct codes can match a bundle; single-procedure claims cannot. */
    public record Coverage(int evaluated, int skippedSingleProcedure) {
    }

    private final int severity;
    private final Catalog catalog;

    public UnbundlingRule(FraudConfig config, Catalog catalog) {
        this.severity = config.weight(RuleId.UNBUNDLING);
        this.catalog = catalog;
    }

    @Override
    public RuleId id() {
        return RuleId.UNBUNDLING;
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        Set<String> codes = new LinkedHashSet<>(claim.procedureCodes());
        if (codes.size() < 2) {
            return Optional.empty();
        }
        List<String> matches = new ArrayList<>();
        int doubleBilled = 0;
        for (Bundle bundle : catalog.bundles()) {
            long bundlePrice = basePrice(bundle.bundleCode());
            if (codes.contains(bundle.bundleCode())) {
                List<String> billedComponents = bundle.components().stream().filter(codes::contains).toList();
                if (!billedComponents.isEmpty()) {
                    doubleBilled++;
                    matches.add(Evidence.format(
                            "bundle %s billed together with its %s %s (%s at base price on top of the bundle price %s)",
                            bundle.bundleCode(), billedComponents.size() == 1 ? "component" : "components",
                            String.join(", ", billedComponents), Evidence.amount(sum(billedComponents)),
                            Evidence.amount(bundlePrice)));
                }
            } else if (codes.containsAll(bundle.components())) {
                matches.add(Evidence.format(
                        "all components of bundle %s billed separately: %s (%s at base price vs bundle price %s)",
                        bundle.bundleCode(), String.join(", ", bundle.components()),
                        Evidence.amount(sum(bundle.components())), Evidence.amount(bundlePrice)));
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        String label = doubleBilled == 0 ? "Unbundling"
                : doubleBilled == matches.size() ? "Double billing"
                : "Unbundling and double billing";
        String evidence = Evidence.format("%s on %d %s: %s", label, matches.size(),
                matches.size() == 1 ? "bundle" : "bundles", String.join("; ", matches));
        return Optional.of(new Flag(id(), severity, evidence));
    }

    public Coverage coverage(DatasetIndex index) {
        int evaluated = 0;
        int single = 0;
        for (Claim c : index.claimsInOrder()) {
            if (new LinkedHashSet<>(c.procedureCodes()).size() < 2) {
                single++;
            } else {
                evaluated++;
            }
        }
        return new Coverage(evaluated, single);
    }

    private long basePrice(String code) {
        return catalog.procedure(code).orElseThrow().basePrice();
    }

    private long sum(List<String> codes) {
        return codes.stream().mapToLong(this::basePrice).sum();
    }
}
