package com.papaya.fraud.io;

import com.papaya.fraud.model.Claim;
import java.util.List;

/**
 * Outcome of reading a claims file. Bad data never throws: rows are either kept (possibly with warnings) or
 * rejected with their line number and reason.
 */
public record LoadResult(List<Claim> claims, List<RejectedRow> rejected, List<Warning> warnings) {

    public LoadResult {
        claims = List.copyOf(claims);
        rejected = List.copyOf(rejected);
        warnings = List.copyOf(warnings);
    }

    /** A row left out of {@link #claims()}. {@code line} is the 1-based line where the row starts. */
    public record RejectedRow(long line, String claimId, String reason) {
    }

    /** A kept row with a problem that did not justify rejecting it. */
    public record Warning(long line, String claimId, String message) {
    }
}
