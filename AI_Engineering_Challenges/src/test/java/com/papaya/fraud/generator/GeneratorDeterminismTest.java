package com.papaya.fraud.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.io.ClaimCsvWriter;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GeneratorDeterminismTest {

    private final DatasetGenerator generator =
            new DatasetGenerator(GeneratorConfigLoader.loadDefault(), CatalogLoader.loadDefault());

    @Test
    void sameSeedGivesByteIdenticalFiles() {
        GeneratedDataset first = generator.generate(42);
        GeneratedDataset second = generator.generate(42);

        assertArrayEquals(ClaimCsvWriter.toBytes(first.claims()), ClaimCsvWriter.toBytes(second.claims()));
        assertArrayEquals(GroundTruthWriter.toBytes(first.groundTruth()), GroundTruthWriter.toBytes(second.groundTruth()));
    }

    @Test
    void differentSeedsGiveDifferentData() {
        assertFalse(Arrays.equals(ClaimCsvWriter.toBytes(generator.generate(42).claims()),
                ClaimCsvWriter.toBytes(generator.generate(2024).claims())));
    }

    @Test
    void stageStreamsDependOnlyOnSeedAndStageName() {
        SplittableRandom a = StageRandom.forStage(42, "base");
        SplittableRandom b = StageRandom.forStage(42, "base");
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        assertNotEquals(StageRandom.forStage(42, "base").nextLong(), StageRandom.forStage(42, "members").nextLong());
        assertNotEquals(StageRandom.forStage(42, "base").nextLong(), StageRandom.forStage(43, "base").nextLong());
        assertEquals(0xaf63dc4c8601ec8cL, StageRandom.fnv1a64("a"), "FNV-1a 64 reference value for \"a\"");
    }
}
