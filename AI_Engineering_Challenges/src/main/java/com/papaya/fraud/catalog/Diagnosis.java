package com.papaya.fraud.catalog;

import com.papaya.fraud.model.ClaimType;
import java.util.EnumSet;
import java.util.Set;

/**
 * An ICD-10 (WHO) diagnosis and the claim types it may appear on.
 */
public record Diagnosis(String code, String description, Set<ClaimType> claimTypes) {

    public Diagnosis {
        claimTypes = claimTypes == null || claimTypes.isEmpty()
                ? EnumSet.noneOf(ClaimType.class)
                : EnumSet.copyOf(claimTypes);
    }
}
