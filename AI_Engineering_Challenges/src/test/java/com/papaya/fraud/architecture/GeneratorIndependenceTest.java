package com.papaya.fraud.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.papaya.fraud.io.Json;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Generator spec section 8 (H2#1): the generator must not know the engine's thresholds, so it may not import the
 * engine or its config, and its own parameters use different names.
 */
class GeneratorIndependenceTest {

    @Test
    void generatorDoesNotUseTheEngineOrItsConfig() {
        List<String> found = SourceScan.occurrences(SourceScan.files("generator"),
                "com.papaya.fraud.engine", "com.papaya.fraud.config", "fraud-config");
        assertEquals(List.of(), found);
        assertFalse(SourceScan.files("generator").isEmpty(), "the scan must actually see the generator sources");
    }

    @Test
    void engineConfigAndIoDoNotDependOnTheGenerator() {
        assertEquals(List.of(), SourceScan.occurrences(SourceScan.files("engine", "config", "io"),
                "com.papaya.fraud.generator"));
    }

    @Test
    void generatorAndEngineConfigFilesShareNoKeyName() throws IOException {
        Set<String> engineKeys = keys("/fraud-config.json");
        Set<String> generatorKeys = keys("/generator-config.json");
        assertTrue(engineKeys.contains("window_days") && generatorKeys.contains("gap_days"), "sanity: both parsed");

        Set<String> shared = new TreeSet<>(engineKeys);
        shared.retainAll(generatorKeys);
        assertEquals(Set.of(), shared);
    }

    private static Set<String> keys(String resource) throws IOException {
        try (InputStream in = GeneratorIndependenceTest.class.getResourceAsStream(resource)) {
            Set<String> keys = new TreeSet<>();
            collect(Json.mapper().readTree(in), keys);
            return keys;
        }
    }

    private static void collect(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                keys.add(e.getKey());
                collect(e.getValue(), keys);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, keys));
        }
    }
}
