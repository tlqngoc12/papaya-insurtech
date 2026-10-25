package com.papaya.fraud.io;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Format of {@code claims.csv}: the ten columns of the challenge, procedure codes joined with {@code |}.
 */
public final class ClaimCsv {

    public static final List<String> COLUMNS = List.of(
            "claim_id", "member_id", "provider_id", "provider_name", "claim_date",
            "claim_type", "diagnosis_code", "procedure_codes", "submitted_amount", "is_weekend");

    public static final String PROCEDURE_SEPARATOR = "|";
    public static final String LINE_SEPARATOR = "\n";

    private ClaimCsv() {
    }

    /** Saturday and Sunday; public holidays are not considered (R7). */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
