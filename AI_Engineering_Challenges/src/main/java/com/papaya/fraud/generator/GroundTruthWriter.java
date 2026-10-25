package com.papaya.fraud.generator;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code ground_truth.csv}: UTF-8 without BOM, {@code \n} line endings, rows in claim_id order. Only the
 * metrics module ever reads this file; the engine never does.
 */
public final class GroundTruthWriter {

    public static final List<String> COLUMNS =
            List.of("claim_id", "is_fraud", "fraud_pattern", "related_claim_id", "scenario");

    private static final CsvMapper MAPPER = new CsvMapper();
    private static final CsvSchema SCHEMA = CsvSchema.builder()
            .addColumns(COLUMNS, CsvSchema.ColumnType.STRING)
            .setLineSeparator("\n")
            .setUseHeader(true)
            .build();

    private GroundTruthWriter() {
    }

    public static void write(List<GroundTruthRow> rows, Path file) {
        try {
            Files.write(file, toBytes(rows));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    public static byte[] toBytes(List<GroundTruthRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (SequenceWriter writer = MAPPER.writerFor(String[].class).with(SCHEMA)
                .writeValues(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            for (GroundTruthRow r : rows) {
                writer.write(new String[] {r.claimId(), Boolean.toString(r.isFraud()), r.fraudPattern(),
                    r.relatedClaimId() == null ? "" : r.relatedClaimId(), r.scenario()});
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
