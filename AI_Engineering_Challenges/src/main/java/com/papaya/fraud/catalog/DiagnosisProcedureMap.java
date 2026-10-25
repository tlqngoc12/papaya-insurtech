package com.papaya.fraud.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw content of {@code diagnosis-procedures.json}.
 *
 * @param genericCodes codes valid with every diagnosis that shares a claim type with them (e.g. consultation)
 * @param valid        diagnosis code to its clinically associated procedure codes, generic codes excluded
 */
public record DiagnosisProcedureMap(List<String> genericCodes, Map<String, List<String>> valid) {

    public DiagnosisProcedureMap {
        genericCodes = genericCodes == null ? List.of() : List.copyOf(genericCodes);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (valid != null) {
            valid.forEach((dx, codes) -> copy.put(dx, codes == null ? List.of() : List.copyOf(codes)));
        }
        valid = Collections.unmodifiableMap(copy);
    }
}
