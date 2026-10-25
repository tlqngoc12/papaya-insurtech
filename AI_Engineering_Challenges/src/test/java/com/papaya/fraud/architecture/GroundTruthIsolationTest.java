package com.papaya.fraud.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hard constraint: the engine never reads ground truth. Only the metrics module (and the generator, which writes
 * it) may mention it; the scoring path must not.
 */
class GroundTruthIsolationTest {

    @Test
    void scoringPathNeverMentionsGroundTruth() {
        List<String> found = SourceScan.occurrences(
                SourceScan.files("engine", "config", "io", "model", "catalog", "cli/ScoreCommand.java"),
                "GroundTruth", "ground_truth", "is_fraud", "fraud_pattern");
        assertEquals(List.of(), found);
    }
}
