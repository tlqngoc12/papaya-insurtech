package com.papaya.fraud.metrics;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads ground_truth.csv (claim_id, is_fraud, fraud_pattern, related_claim_id, scenario; D3 with G5). Package-private:
 * only the metrics module may touch ground truth. Unlike the claims reader it fails on any bad row, because the file is
 * our own generator output and a silent skip would distort every metric.
 */
final class GroundTruthReader {

    static final List<String> COLUMNS =
            List.of("claim_id", "is_fraud", "fraud_pattern", "related_claim_id", "scenario");

    private static final CsvMapper MAPPER = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY);

    private GroundTruthReader() {
    }

    static Map<String, GroundTruthLabel> read(Path file) {
        try {
            return read(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    static Map<String, GroundTruthLabel> read(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        Map<String, GroundTruthLabel> labels = new LinkedHashMap<>();
        try (MappingIterator<String[]> rows = MAPPER.readerFor(String[].class)
                .with(CsvParser.Feature.SKIP_EMPTY_LINES).readValues(text)) {
            if (!rows.hasNextValue() || !Arrays.asList(rows.nextValue()).equals(COLUMNS)) {
                throw new IllegalArgumentException("ground truth header must be " + String.join(",", COLUMNS));
            }
            int line = 1;
            while (rows.hasNextValue()) {
                String[] row = rows.nextValue();
                line++;
                if (row.length != COLUMNS.size()) {
                    throw new IllegalArgumentException("ground truth row " + line + ": expected 5 columns");
                }
                if (!row[1].equals("true") && !row[1].equals("false")) {
                    throw new IllegalArgumentException("ground truth row " + line + ": is_fraud must be true or false");
                }
                GroundTruthLabel label = new GroundTruthLabel(row[0], Boolean.parseBoolean(row[1]), row[2], row[3],
                        row[4]);
                if (labels.put(label.claimId(), label) != null) {
                    throw new IllegalArgumentException("ground truth row " + line + ": duplicate claim_id "
                            + label.claimId());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("malformed ground truth CSV", e);
        }
        return labels;
    }
}
