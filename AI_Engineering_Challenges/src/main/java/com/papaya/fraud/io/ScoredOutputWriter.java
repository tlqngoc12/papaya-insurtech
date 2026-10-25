package com.papaya.fraud.io;

import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.ScoredClaim;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code scored_claims.json}: a JSON array of every claim in rank order, each exactly
 * {@code {claim_id, risk_score, flags: [{rule, severity, evidence}]}} (O1, O2). UTF-8 without BOM, two-space
 * indentation, {@code \n} line endings whatever the operating system, empty flag lists written as {@code []}.
 */
public final class ScoredOutputWriter {

    record OutputFlag(String rule, int severity, String evidence) {
    }

    record OutputClaim(String claimId, int riskScore, List<OutputFlag> flags) {
    }

    private ScoredOutputWriter() {
    }

    /** Creates missing parent directories, like the other commands' writers; the bytes do not depend on it. */
    public static void write(List<ScoredClaim> ranked, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, toBytes(ranked));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    public static byte[] toBytes(List<ScoredClaim> ranked) {
        List<OutputClaim> output = ranked.stream()
                .map(s -> new OutputClaim(s.claim().claimId(), s.riskScore(),
                        s.flags().stream().map(ScoredOutputWriter::toOutput).toList()))
                .toList();
        return Json.prettyBytes(output);
    }

    private static OutputFlag toOutput(Flag flag) {
        return new OutputFlag(flag.rule().id(), flag.severity(), flag.evidence());
    }
}
