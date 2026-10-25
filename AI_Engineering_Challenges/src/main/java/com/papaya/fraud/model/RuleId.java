package com.papaya.fraud.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The eight detection rules, in the order of the challenge. {@link #id()} is the name used in output,
 * config and reports (O5).
 */
public enum RuleId {
    DUPLICATE_CLAIM("duplicate_claim"),
    RAPID_RESUBMISSION("rapid_resubmission"),
    UPCODING("upcoding"),
    UNBUNDLING("unbundling"),
    PHANTOM_BILLING("phantom_billing"),
    WEEKEND_ANOMALY("weekend_anomaly"),
    DIAGNOSIS_PROCEDURE_MISMATCH("diagnosis_procedure_mismatch"),
    AMOUNT_CLUSTERING("amount_clustering");

    private final String id;

    RuleId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<RuleId> fromId(String id) {
        return Arrays.stream(values()).filter(r -> r.id.equals(id)).findFirst();
    }
}
