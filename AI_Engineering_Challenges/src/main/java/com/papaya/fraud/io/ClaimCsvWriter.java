package com.papaya.fraud.io;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.papaya.fraud.model.Claim;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code claims.csv}: UTF-8 without BOM, {@code \n} line endings, ISO dates, plain integer amounts.
 * Every value is converted to text here, so the default locale never affects the output.
 */
public final class ClaimCsvWriter {

    private static final CsvMapper MAPPER = new CsvMapper();
    private static final CsvSchema SCHEMA = CsvSchema.builder()
            .addColumns(ClaimCsv.COLUMNS, CsvSchema.ColumnType.STRING)
            .setLineSeparator(ClaimCsv.LINE_SEPARATOR)
            .setUseHeader(true)
            .build();

    private ClaimCsvWriter() {
    }

    public static void write(List<Claim> claims, Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            write(claims, out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    public static byte[] toBytes(List<Claim> claims) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            write(claims, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static void write(List<Claim> claims, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        try (SequenceWriter rows = MAPPER.writerFor(String[].class).with(SCHEMA).writeValues(writer)) {
            for (Claim c : claims) {
                rows.write(toRow(c));
            }
        }
    }

    static String[] toRow(Claim c) {
        return new String[] {
            c.claimId(),
            c.memberId(),
            c.providerId(),
            c.providerName(),
            c.claimDate().toString(),
            c.claimType().name(),
            c.diagnosisCode(),
            String.join(ClaimCsv.PROCEDURE_SEPARATOR, c.procedureCodes()),
            Long.toString(c.submittedAmount()),
            Boolean.toString(c.isWeekend())
        };
    }
}
