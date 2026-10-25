package com.papaya.fraud.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.io.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorConfigLoaderTest {

    @TempDir
    Path tmp;

    private static ObjectNode defaultTree() throws IOException {
        try (InputStream in = GeneratorConfigLoader.class.getResourceAsStream(GeneratorConfigLoader.DEFAULT_RESOURCE)) {
            return (ObjectNode) Json.mapper().readTree(in);
        }
    }

    private String problemOf(ObjectNode tree) throws IOException {
        Path file = tmp.resolve("generator-config.json");
        Json.mapper().writeValue(file.toFile(), tree);
        return assertThrows(IllegalArgumentException.class, () -> GeneratorConfigLoader.load(file)).getMessage();
    }

    @Test
    void defaultConfigAddsUp() {
        GeneratorConfig c = GeneratorConfigLoader.loadDefault();
        assertEquals(200, c.fraudCounts().total());
        assertEquals(25, c.noise().nearMiss().total());
        assertEquals(1_719, c.baseClaims(), "2,000 - 200 fraud - 20 band noise - 6 phantom-day legit - 25 near-miss - 30");
        assertEquals(122, c.bandHostBaseClaims(), "4 x 32 natural minus the 6 band near-misses");
    }

    @Test
    void rejectsABandHostAboveTheQuarterPlantedShare() throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.withArray("band_hosts").get(0)).put("natural_claims", 30).put("planted_claims", 11);
        String message = problemOf(tree);
        assertTrue(message.contains("planted share"), message);
    }

    @Test
    void rejectsAWeekdayOnlyQuotaBelowSixty() throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.get("volumes")).put("weekday_only_hospital_claims", 59);
        assertTrue(problemOf(tree).contains("weekday_only_hospital_claims must be >= 60 (Q13)"));
    }

    @Test
    void rejectsUpcodeCountsThatDoNotAddUp() throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.get("upcode")).put("mild_claims", 9);
        assertTrue(problemOf(tree).contains("upcode strong_claims + mild_claims must equal fraud_counts.upcode"));
    }
}
