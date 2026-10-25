package com.papaya.fraud.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Validated reference data shared by the generator and the engine: diagnoses, procedures, bundles and the
 * diagnosis-procedure whitelist. Lookups and iteration order are deterministic.
 */
public final class Catalog {

    static final int MIN_BUNDLE_COMPONENTS = 2;
    static final int MAX_BUNDLE_COMPONENTS = 4;

    private final List<Procedure> procedures;
    private final List<Diagnosis> diagnoses;
    private final List<Bundle> bundles;
    private final SortedSet<String> genericCodes;
    private final Map<String, Procedure> proceduresByCode;
    private final Map<String, Diagnosis> diagnosesByCode;
    private final Map<String, SortedSet<String>> specificByDiagnosis;

    private Catalog(List<Procedure> procedures, List<Diagnosis> diagnoses, List<Bundle> bundles,
                    DiagnosisProcedureMap whitelist) {
        this.procedures = List.copyOf(procedures);
        this.diagnoses = List.copyOf(diagnoses);
        this.bundles = List.copyOf(bundles);
        this.genericCodes = Collections.unmodifiableSortedSet(new TreeSet<>(whitelist.genericCodes()));
        this.proceduresByCode = indexBy(procedures, Procedure::code);
        this.diagnosesByCode = indexBy(diagnoses, Diagnosis::code);
        Map<String, SortedSet<String>> specific = new LinkedHashMap<>();
        whitelist.valid().forEach((dx, codes) ->
                specific.put(dx, Collections.unmodifiableSortedSet(new TreeSet<>(codes))));
        this.specificByDiagnosis = Collections.unmodifiableMap(specific);
    }

    /**
     * Builds a catalog, rejecting it with every problem found if the data is inconsistent.
     */
    public static Catalog create(List<Procedure> procedures, List<Diagnosis> diagnoses, List<Bundle> bundles,
                                 DiagnosisProcedureMap whitelist) {
        List<String> problems = new ArrayList<>();
        validateProcedures(procedures, problems);
        validateDiagnoses(diagnoses, problems);
        Map<String, Procedure> procs = indexBy(procedures, Procedure::code);
        Map<String, Diagnosis> dxs = indexBy(diagnoses, Diagnosis::code);
        validateWhitelist(whitelist, procs, dxs, problems);
        validateBundles(bundles, procs, problems);
        if (!problems.isEmpty()) {
            throw new CatalogException(problems);
        }
        Catalog catalog = new Catalog(procedures, diagnoses, bundles, whitelist);
        for (Bundle bundle : bundles) {
            if (!catalog.bundleValidForSomeDiagnosis(bundle)) {
                problems.add("bundle " + bundle.bundleCode()
                        + ": no diagnosis allows the bundle code together with all its components");
            }
        }
        if (!problems.isEmpty()) {
            throw new CatalogException(problems);
        }
        return catalog;
    }

    public List<Procedure> procedures() {
        return procedures;
    }

    public List<Diagnosis> diagnoses() {
        return diagnoses;
    }

    public List<Bundle> bundles() {
        return bundles;
    }

    public SortedSet<String> genericCodes() {
        return genericCodes;
    }

    public Optional<Procedure> procedure(String code) {
        return Optional.ofNullable(proceduresByCode.get(code));
    }

    public Optional<Diagnosis> diagnosis(String code) {
        return Optional.ofNullable(diagnosesByCode.get(code));
    }

    /** Whether the whitelist covers this diagnosis (R8: unknown diagnoses are never flagged). */
    public boolean hasWhitelistFor(String diagnosisCode) {
        return specificByDiagnosis.containsKey(diagnosisCode);
    }

    /** Clinically associated procedures for a diagnosis, excluding generic codes. */
    public SortedSet<String> specificProcedures(String diagnosisCode) {
        return specificByDiagnosis.getOrDefault(diagnosisCode, Collections.emptySortedSet());
    }

    /** Generic codes valid for a diagnosis: those sharing at least one claim type with it. */
    public SortedSet<String> genericProceduresFor(String diagnosisCode) {
        Diagnosis dx = diagnosesByCode.get(diagnosisCode);
        if (dx == null) {
            return Collections.emptySortedSet();
        }
        SortedSet<String> result = new TreeSet<>();
        for (String code : genericCodes) {
            if (proceduresByCode.get(code).sharesClaimTypeWith(dx)) {
                result.add(code);
            }
        }
        return Collections.unmodifiableSortedSet(result);
    }

    /** All procedures valid for a diagnosis: specific plus applicable generic codes. */
    public SortedSet<String> validProcedures(String diagnosisCode) {
        SortedSet<String> result = new TreeSet<>(specificProcedures(diagnosisCode));
        result.addAll(genericProceduresFor(diagnosisCode));
        return Collections.unmodifiableSortedSet(result);
    }

    public boolean isValidPair(String diagnosisCode, String procedureCode) {
        return specificProcedures(diagnosisCode).contains(procedureCode)
                || genericProceduresFor(diagnosisCode).contains(procedureCode);
    }

    private boolean bundleValidForSomeDiagnosis(Bundle bundle) {
        return diagnoses.stream().anyMatch(dx -> isValidPair(dx.code(), bundle.bundleCode())
                && bundle.components().stream().allMatch(c -> isValidPair(dx.code(), c)));
    }

    private static void validateProcedures(List<Procedure> procedures, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (Procedure p : procedures) {
            if (!seen.add(p.code())) {
                problems.add("procedure " + p.code() + ": duplicate code");
            }
            if (p.claimTypes().isEmpty()) {
                problems.add("procedure " + p.code() + ": no claim types");
            }
            if (p.basePrice() <= 0) {
                problems.add("procedure " + p.code() + ": base price must be positive");
            }
        }
    }

    private static void validateDiagnoses(List<Diagnosis> diagnoses, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (Diagnosis d : diagnoses) {
            if (!seen.add(d.code())) {
                problems.add("diagnosis " + d.code() + ": duplicate code");
            }
            if (d.claimTypes().isEmpty()) {
                problems.add("diagnosis " + d.code() + ": no claim types");
            }
        }
    }

    private static void validateWhitelist(DiagnosisProcedureMap whitelist, Map<String, Procedure> procs,
                                          Map<String, Diagnosis> dxs, List<String> problems) {
        Set<String> generic = new HashSet<>(whitelist.genericCodes());
        for (String code : whitelist.genericCodes()) {
            if (!procs.containsKey(code)) {
                problems.add("whitelist: unknown generic code " + code);
            }
        }
        for (String dx : dxs.keySet()) {
            if (!whitelist.valid().containsKey(dx)) {
                problems.add("whitelist: diagnosis " + dx + " has no entry");
            }
        }
        whitelist.valid().forEach((dxCode, codes) -> {
            Diagnosis dx = dxs.get(dxCode);
            if (dx == null) {
                problems.add("whitelist: unknown diagnosis " + dxCode);
                return;
            }
            Set<String> seen = new HashSet<>();
            for (String code : codes) {
                Procedure p = procs.get(code);
                if (!seen.add(code)) {
                    problems.add("whitelist " + dxCode + ": duplicate procedure " + code);
                } else if (p == null) {
                    problems.add("whitelist " + dxCode + ": unknown procedure " + code);
                } else if (generic.contains(code)) {
                    problems.add("whitelist " + dxCode + ": generic code " + code + " must not be listed");
                } else if (!p.sharesClaimTypeWith(dx)) {
                    problems.add("whitelist " + dxCode + " + " + code + ": no shared claim type");
                }
            }
        });
    }

    private static void validateBundles(List<Bundle> bundles, Map<String, Procedure> procs, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (Bundle b : bundles) {
            String code = b.bundleCode();
            if (!seen.add(code)) {
                problems.add("bundle " + code + ": duplicate bundle code");
            }
            if (!procs.containsKey(code)) {
                problems.add("bundle " + code + ": unknown bundle code");
            }
            int n = b.components().size();
            if (n < MIN_BUNDLE_COMPONENTS || n > MAX_BUNDLE_COMPONENTS) {
                problems.add("bundle " + code + ": needs " + MIN_BUNDLE_COMPONENTS + "-" + MAX_BUNDLE_COMPONENTS
                        + " components, has " + n);
            }
            if (new HashSet<>(b.components()).size() != n) {
                problems.add("bundle " + code + ": duplicate component");
            }
            for (String component : b.components()) {
                if (component.equals(code)) {
                    problems.add("bundle " + code + ": contains itself");
                } else if (!procs.containsKey(component)) {
                    problems.add("bundle " + code + ": unknown component " + component);
                }
            }
        }
    }

    private static <T> Map<String, T> indexBy(List<T> items, Function<T, String> key) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.putIfAbsent(key.apply(item), item);
        }
        return Collections.unmodifiableMap(map);
    }
}
