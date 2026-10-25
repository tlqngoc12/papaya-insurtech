package com.papaya.fraud.testing;

import com.papaya.fraud.engine.DatasetIndex;
import com.papaya.fraud.io.ClaimCsv;
import com.papaya.fraud.model.Claim;
import com.papaya.fraud.model.ClaimType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Test fixture: starts from a valid outpatient claim (Monday 2024-03-11, J06.9 + P-1001, 300) and changes one
 * field at a time. {@code is_weekend} follows the date unless set explicitly, as {@code ClaimCsvReader} does.
 */
public final class ClaimBuilder {

    public static final LocalDate MONDAY = LocalDate.of(2024, 3, 11);
    public static final LocalDate SATURDAY = LocalDate.of(2024, 3, 16);

    private String claimId;
    private String memberId = "M-0001";
    private String providerId = "PRV-01";
    private String providerName = "Test Clinic";
    private LocalDate claimDate = MONDAY;
    private ClaimType claimType = ClaimType.OUTPATIENT;
    private String diagnosisCode = "J06.9";
    private List<String> procedureCodes = List.of("P-1001");
    private long submittedAmount = 300;
    private Boolean weekendOverride;

    private ClaimBuilder(String claimId) {
        this.claimId = claimId;
    }

    public static ClaimBuilder claim(String claimId) {
        return new ClaimBuilder(claimId);
    }

    public static DatasetIndex indexOf(Claim... claims) {
        return DatasetIndex.of(Arrays.asList(claims));
    }

    public static DatasetIndex indexOf(List<Claim> claims) {
        return DatasetIndex.of(claims);
    }

    public ClaimBuilder withId(String id) {
        this.claimId = id;
        return this;
    }

    public ClaimBuilder withMember(String id) {
        this.memberId = id;
        return this;
    }

    public ClaimBuilder withProvider(String id) {
        this.providerId = id;
        return this;
    }

    public ClaimBuilder withProviderName(String name) {
        this.providerName = name;
        return this;
    }

    public ClaimBuilder withDate(LocalDate date) {
        this.claimDate = date;
        return this;
    }

    public ClaimBuilder withDate(String isoDate) {
        return withDate(LocalDate.parse(isoDate));
    }

    public ClaimBuilder withType(ClaimType type) {
        this.claimType = type;
        return this;
    }

    public ClaimBuilder withDiagnosis(String code) {
        this.diagnosisCode = code;
        return this;
    }

    public ClaimBuilder withProcedures(String... codes) {
        this.procedureCodes = List.of(codes);
        return this;
    }

    public ClaimBuilder withAmount(long amount) {
        this.submittedAmount = amount;
        return this;
    }

    /** Only for tests of inconsistent input; normally is_weekend is derived from the date. */
    public ClaimBuilder withWeekendFlag(boolean weekend) {
        this.weekendOverride = weekend;
        return this;
    }

    public Claim build() {
        boolean weekend = weekendOverride != null ? weekendOverride : ClaimCsv.isWeekend(claimDate);
        return new Claim(claimId, memberId, providerId, providerName, claimDate, claimType, diagnosisCode,
                procedureCodes, submittedAmount, weekend);
    }
}
