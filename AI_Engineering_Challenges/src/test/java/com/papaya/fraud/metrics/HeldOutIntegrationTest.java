package com.papaya.fraud.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.FraudEngine;
import com.papaya.fraud.generator.DatasetGenerator;
import com.papaya.fraud.generator.GeneratedDataset;
import com.papaya.fraud.generator.GeneratorConfigLoader;
import com.papaya.fraud.generator.GroundTruthWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Test #37 (Q18): the challenge targets on the held-out dataset (seed 2024), scored with the config frozen on the
 * tuning dataset (tag frozen-before-heldout). Written before the held-out data was ever generated or looked at.
 * T is read from config/frozen-config.json, never hard-coded; only the challenge targets are asserted.
 */
class HeldOutIntegrationTest {

    private static final long HELD_OUT_SEED = 2024;
    private static final Path FROZEN_CONFIG = Path.of("config", "frozen-config.json");

    @Test
    void heldOutMeetsTheChallengeTargetsWithTheFrozenConfig() {
        FraudConfig config = ConfigLoader.load(FROZEN_CONFIG);
        int threshold = config.requireThreshold();
        Catalog catalog = CatalogLoader.loadDefault();
        GeneratedDataset dataset = new DatasetGenerator(GeneratorConfigLoader.loadDefault(), catalog)
                .generate(HELD_OUT_SEED);
        assertEquals(2_000, dataset.claims().size());

        // The challenge: processing 2,000 claims completes in under 30 seconds.
        FraudEngine.Result result = assertTimeout(Duration.ofSeconds(30),
                () -> new FraudEngine(config, catalog).run(dataset.claims()));

        Map<String, GroundTruthLabel> labels = GroundTruthReader.read(GroundTruthWriter.toBytes(dataset.groundTruth()));
        ConfusionMatrix c = MetricsCalculator.confusion(result.ranked(), labels, threshold);
        System.out.printf(Locale.US, "Held-out seed %d at T = %d: TP %d, FP %d, FN %d, TN %d, recall %.4f, FPR %.4f%n",
                HELD_OUT_SEED, threshold, c.tp(), c.fp(), c.fn(), c.tn(), c.recall(), c.fpr());

        assertTrue(c.recall() >= 0.70, "recall " + c.recall() + " is below the 70% target");
        assertTrue(c.fprAtMost(0.20), "standard FPR " + c.fpr() + " is above the 20% target");
    }
}
