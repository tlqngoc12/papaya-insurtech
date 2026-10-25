package com.papaya.fraud.testing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.io.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Test fixture: the bundled {@code fraud-config.json} with one change applied, loaded through
 * {@link ConfigLoader} so the variant is validated like a real file.
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static FraudConfig defaultWith(Consumer<ObjectNode> change) {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/fraud-config.json")) {
            ObjectNode tree = (ObjectNode) Json.mapper().readTree(in);
            change.accept(tree);
            Path file = Files.createTempFile("fraud-config-", ".json");
            try {
                Json.mapper().writeValue(file.toFile(), tree);
                return ConfigLoader.load(file);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FraudConfig withUpcodingMinGroupSize(int n) {
        return defaultWith(t -> ((ObjectNode) t.at("/rules/upcoding")).put("min_group_size", n));
    }
}
