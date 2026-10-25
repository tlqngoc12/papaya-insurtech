package com.papaya.fraud.generator;

import com.papaya.fraud.model.ClaimType;
import java.util.EnumSet;
import java.util.Set;

/** A generated provider: its tier decides which claim types it serves and whether it ever works weekends. */
public record ProviderProfile(String id, String name, Tier tier, double weight) {

    public enum Tier {
        WEEKDAY_ONLY_HOSPITAL(EnumSet.of(ClaimType.INPATIENT, ClaimType.OUTPATIENT)),
        REGULAR_HOSPITAL(EnumSet.of(ClaimType.INPATIENT, ClaimType.OUTPATIENT)),
        CLINIC(EnumSet.of(ClaimType.OUTPATIENT)),
        DENTAL_CLINIC(EnumSet.of(ClaimType.DENTAL));

        private final Set<ClaimType> claimTypes;

        Tier(Set<ClaimType> claimTypes) {
            this.claimTypes = claimTypes;
        }

        public Set<ClaimType> claimTypes() {
            return claimTypes;
        }
    }

    public boolean weekdayOnly() {
        return tier == Tier.WEEKDAY_ONLY_HOSPITAL;
    }

    public boolean hospital() {
        return tier == Tier.WEEKDAY_ONLY_HOSPITAL || tier == Tier.REGULAR_HOSPITAL;
    }
}
