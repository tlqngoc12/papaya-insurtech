package com.papaya.fraud.catalog;

import com.papaya.fraud.model.ClaimType;
import java.util.EnumSet;
import java.util.Set;

/**
 * A billable procedure. {@code surgical} is this project's own classification (see limitations L6).
 */
public record Procedure(String code, String description, Set<ClaimType> claimTypes, long basePrice, boolean surgical) {

    public Procedure {
        // EnumSet keeps a stable iteration order; Set.copyOf would not across JVM runs.
        claimTypes = claimTypes == null || claimTypes.isEmpty()
                ? EnumSet.noneOf(ClaimType.class)
                : EnumSet.copyOf(claimTypes);
    }

    public boolean sharesClaimTypeWith(Diagnosis diagnosis) {
        return claimTypes.stream().anyMatch(diagnosis.claimTypes()::contains);
    }
}
