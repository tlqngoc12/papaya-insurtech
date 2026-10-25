package com.papaya.fraud.generator;

import com.papaya.fraud.io.ClaimCsvWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a generated dataset as {@code claims.csv} plus its separate {@code ground_truth.csv} (D3). */
public final class DatasetFiles {

    public static final String CLAIMS = "claims.csv";
    public static final String LABELS = "ground_truth.csv";

    /** Where the two files were written. */
    public record Written(Path claims, Path labels) {
    }

    private DatasetFiles() {
    }

    public static Written write(GeneratedDataset dataset, Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + dir, e);
        }
        Path claims = dir.resolve(CLAIMS);
        Path labels = dir.resolve(LABELS);
        ClaimCsvWriter.write(dataset.claims(), claims);
        GroundTruthWriter.write(dataset.groundTruth(), labels);
        return new Written(claims, labels);
    }
}
