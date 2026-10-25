package com.papaya.fraud.io;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.ScoredClaim;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

    /** Jackson's default indenter uses the system line separator and writes empty arrays as "[ ]". */
    static final class LfPrettyPrinter extends DefaultPrettyPrinter {

        LfPrettyPrinter() {
            super(DefaultPrettyPrinter.DEFAULT_SEPARATORS
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
            DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
            indentObjectsWith(indenter);
            indentArraysWith(indenter);
        }

        LfPrettyPrinter(LfPrettyPrinter base) {
            super(base);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new LfPrettyPrinter(this);
        }

        @Override
        public void writeEndArray(JsonGenerator g, int nrOfValues) throws IOException {
            if (nrOfValues == 0) {
                if (!_arrayIndenter.isInline()) {
                    --_nesting;
                }
                g.writeRaw(']');
                return;
            }
            super.writeEndArray(g, nrOfValues);
        }
    }

    private static final ObjectWriter WRITER = Json.mapper().writer(new LfPrettyPrinter());

    private ScoredOutputWriter() {
    }

    public static void write(List<ScoredClaim> ranked, Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(toBytes(ranked));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    public static byte[] toBytes(List<ScoredClaim> ranked) {
        List<OutputClaim> output = ranked.stream()
                .map(s -> new OutputClaim(s.claim().claimId(), s.riskScore(),
                        s.flags().stream().map(ScoredOutputWriter::toOutput).toList()))
                .toList();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            WRITER.writeValue(bytes, output);
            bytes.write("\n".getBytes(StandardCharsets.UTF_8));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static OutputFlag toOutput(Flag flag) {
        return new OutputFlag(flag.rule().id(), flag.severity(), flag.evidence());
    }
}
