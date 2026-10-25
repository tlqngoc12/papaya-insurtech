package com.papaya.fraud.model;

import java.time.LocalDate;
import java.util.List;

/**
 * One claim with exactly the ten columns of the challenge dataset, in the same order.
 * {@code procedureCodes} keeps the submitted order.
 */
public record Claim(String claimId, String memberId, String providerId, String providerName, LocalDate claimDate,
                    ClaimType claimType, String diagnosisCode, List<String> procedureCodes, long submittedAmount,
                    boolean isWeekend) {

    public Claim {
        procedureCodes = List.copyOf(procedureCodes);
    }
}
