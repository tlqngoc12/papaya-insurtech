package com.papaya.fraud.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hard constraint: the engine never reads ground truth. Only the metrics module (which reads it) and the generator
 * (which writes it) may touch it; every other package, and every command except generate, must not mention it.
 */
class GroundTruthIsolationTest {

    private static final String[] TOKENS = {"GroundTruth", "ground_truth", "is_fraud", "fraud_pattern"};

    @Test
    void onlyMetricsAndGeneratorTouchGroundTruth() {
        List<Path> files = new ArrayList<>(
                SourceScan.files("engine", "config", "io", "model", "catalog", "pipeline", "Main.java"));
        List<Path> cli = SourceScan.files("cli").stream()
                .filter(p -> !p.getFileName().toString().equals("GenerateCommand.java")).toList();
        assertTrue(cli.size() >= 5, "the scan must see the cli commands: " + cli);
        files.addAll(cli);

        assertEquals(List.of(), SourceScan.occurrences(files, TOKENS));
    }

    @Test
    void groundTruthReaderIsNotPublic() throws Exception {
        String source = Files.readString(SourceScan.MAIN.resolve("metrics/GroundTruthReader.java"));
        assertTrue(source.contains("\nfinal class GroundTruthReader"), "GroundTruthReader must stay package-private");
    }
}
