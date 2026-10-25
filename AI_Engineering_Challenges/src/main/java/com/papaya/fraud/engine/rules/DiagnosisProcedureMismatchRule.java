package com.papaya.fraud.engine.rules;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R8: flagged when the diagnosis is in the whitelist and at least one procedure of the claim is not valid for it
 * (Q14: any invalid procedure; generic codes are valid for diagnoses sharing a claim type). Unknown diagnoses are
 * not flagged. Procedures missing from the catalog are ignored, not judged, and counted in the coverage.
 */
public final class DiagnosisProcedureMismatchRule implements Rule {

    /**
     * {@code withUnknownProcedure} is the subset of evaluated claims carrying at least one code missing from the
     * catalog; those codes were ignored.
     */
    public record Coverage(int evaluated, int skippedUnknownDiagnosis, int withUnknownProcedure) {
    }

    private final int severity;
    private final Catalog catalog;

    public DiagnosisProcedureMismatchRule(FraudConfig config, Catalog catalog) {
        this.severity = config.weight(RuleId.DIAGNOSIS_PROCEDURE_MISMATCH);
        this.catalog = catalog;
    }

    @Override
    public RuleId id() {
        return RuleId.DIAGNOSIS_PROCEDURE_MISMATCH;
    }

    @Override
    public Optional<Flag> evaluate(Claim claim, DatasetIndex index) {
        String dx = claim.diagnosisCode();
        if (!catalog.hasWhitelistFor(dx)) {
            return Optional.empty();
        }
        List<String> invalid = new LinkedHashSet<>(claim.procedureCodes()).stream()
                .filter(code -> catalog.procedure(code).isPresent())
                .filter(code -> !catalog.isValidPair(dx, code))
                .toList();
        if (invalid.isEmpty()) {
            return Optional.empty();
        }
        List<String> described = invalid.stream()
                .map(code -> code + " (" + catalog.procedure(code).orElseThrow().description() + ")")
                .toList();
        String evidence = Evidence.format(
                "%s %s %s not clinically associated with diagnosis %s (%s); valid procedures for %s: %s",
                invalid.size() == 1 ? "Procedure" : "Procedures", String.join(", ", described),
                invalid.size() == 1 ? "is" : "are", dx, catalog.diagnosis(dx).orElseThrow().description(), dx,
                String.join(", ", catalog.validProcedures(dx)));
        return Optional.of(new Flag(id(), severity, evidence));
    }

    public Coverage coverage(DatasetIndex index) {
        int evaluated = 0;
        int unknownDiagnosis = 0;
        int unknownProcedure = 0;
        for (Claim c : index.claimsInOrder()) {
            if (!catalog.hasWhitelistFor(c.diagnosisCode())) {
                unknownDiagnosis++;
                continue;
            }
            evaluated++;
            Set<String> codes = new LinkedHashSet<>(c.procedureCodes());
            if (codes.stream().anyMatch(code -> catalog.procedure(code).isEmpty())) {
                unknownProcedure++;
            }
        }
        return new Coverage(evaluated, unknownDiagnosis, unknownProcedure);
    }
}
