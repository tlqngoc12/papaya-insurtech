package com.papaya.fraud.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.io.Json;
import com.papaya.fraud.model.RuleId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigLoaderTest {

    @TempDir
    Path tmp;

    /** The bundled config as an editable JSON tree, so each test changes exactly one thing. */
    private static ObjectNode defaultTree() throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(ConfigLoader.DEFAULT_RESOURCE)) {
            return (ObjectNode) Json.mapper().readTree(in);
        }
    }

    private Path write(ObjectNode tree) throws IOException {
        Path file = tmp.resolve("config.json");
        Json.mapper().writeValue(file.toFile(), tree);
        return file;
    }

    private List<String> problemsOf(ObjectNode tree) throws IOException {
        Path file = write(tree);
        return assertThrows(ConfigException.class, () -> ConfigLoader.load(file)).problems();
    }

    @Test
    void defaultConfigHasSectionGWeightsSpecParametersAndNoThreshold() {
        FraudConfig config = ConfigLoader.loadDefault();

        assertEquals(Map.of(
                RuleId.DUPLICATE_CLAIM, 5, RuleId.RAPID_RESUBMISSION, 2, RuleId.UPCODING, 4,
                RuleId.UNBUNDLING, 4, RuleId.PHANTOM_BILLING, 5, RuleId.WEEKEND_ANOMALY, 3,
                RuleId.DIAGNOSIS_PROCEDURE_MISMATCH, 3, RuleId.AMOUNT_CLUSTERING, 2), config.weights());
        assertEquals(28, config.totalWeight());

        RuleParams p = config.params();
        assertEquals(7, p.rapidResubmission().windowDays());
        assertEquals(2.0, p.upcoding().zThreshold());
        assertEquals(10, p.upcoding().minGroupSize());
        assertEquals(30, p.phantomBilling().maxClaimsPerDay());
        assertEquals(0.05, p.weekendAnomaly().maxWeekendRatio());
        assertEquals(20, p.weekendAnomaly().minHistory());
        assertEquals(47_500, p.amountClustering().lowerBound());
        assertEquals(49_999, p.amountClustering().upperBound());
        assertEquals(50_000, p.amountClustering().autoApprovalThreshold());

        assertTrue(config.threshold().isEmpty(), "the default config must not carry a threshold T");
    }

    @Test
    void requireThresholdFailsClearlyWhenTIsAbsent() {
        FraudConfig config = ConfigLoader.loadDefault();
        ConfigException e = assertThrows(ConfigException.class, config::requireThreshold);
        assertTrue(e.getMessage().contains("No threshold T"), e.getMessage());
        assertTrue(e.getMessage().contains("config/frozen-config.json"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void rejectsWeightOutsideOneToFive(int weight) throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.get("weights")).put("upcoding", weight);
        assertEquals(List.of("weights.upcoding: must be 1-5, was " + weight), problemsOf(tree));
    }

    @Test
    void rejectsMissingAndUnknownRuleWeights() throws IOException {
        ObjectNode tree = defaultTree();
        ObjectNode weights = (ObjectNode) tree.get("weights");
        weights.remove("unbundling");
        weights.put("upcodeing", 4);
        List<String> problems = problemsOf(tree);
        assertTrue(problems.contains("weights: unknown rule 'upcodeing'"), problems.toString());
        assertTrue(problems.contains("weights.unbundling: missing"), problems.toString());
    }

    @Test
    void rejectsInvalidRuleParametersAllAtOnce() throws IOException {
        ObjectNode tree = defaultTree();
        ObjectNode rules = (ObjectNode) tree.get("rules");
        ((ObjectNode) rules.get("upcoding")).put("min_group_size", 0);
        ((ObjectNode) rules.get("amount_clustering")).put("lower_bound", 50_000).put("upper_bound", 47_500);
        ((ObjectNode) rules.get("weekend_anomaly")).put("max_weekend_ratio", 1.0);
        ((ObjectNode) rules.get("rapid_resubmission")).put("window_days", 0);

        assertEquals(List.of(
                "rules.rapid_resubmission.window_days: must be >= 1, was 0",
                "rules.upcoding.min_group_size: must be >= 2, was 0",
                "rules.weekend_anomaly.max_weekend_ratio: must be between 0 and 1 exclusive, was 1.0",
                "rules.amount_clustering: lower_bound 50000 is above upper_bound 47500"), problemsOf(tree));
    }

    @Test
    void rejectsClusteringUpperBoundNotBelowAutoApprovalThreshold() throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.at("/rules/amount_clustering")).put("upper_bound", 50_000);
        assertEquals(List.of("rules.amount_clustering: upper_bound 50000 must be below auto_approval_threshold 50000"),
                problemsOf(tree));
    }

    @Test
    void clusteringBoundsAreReadAsLong() throws IOException {
        // Values above Integer.MAX_VALUE (2,147,483,647) survive only if the fields are long.
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.at("/rules/amount_clustering"))
                .put("lower_bound", 3_000_000_000L)
                .put("upper_bound", 3_000_000_001L)
                .put("auto_approval_threshold", 3_000_000_002L);
        RuleParams.Clustering c = ConfigLoader.load(write(tree)).params().amountClustering();
        assertEquals(3_000_000_000L, c.lowerBound());
        assertEquals(3_000_000_001L, c.upperBound());
        assertEquals(3_000_000_002L, c.autoApprovalThreshold());
    }

    @Test
    void rejectsThresholdOutsideOneToHundred() throws IOException {
        ObjectNode tree = defaultTree();
        tree.put("threshold", 0);
        assertEquals(List.of("threshold: must be 1-100, was 0"), problemsOf(tree));
    }

    @Test
    void configFlagOverridesTheBundledDefault() throws IOException {
        ObjectNode tree = defaultTree();
        ((ObjectNode) tree.get("weights")).put("amount_clustering", 1);
        // T = 7 is fake test data to exercise the override; the real T comes only from 'tune' (Task 17).
        tree.put("threshold", 7);
        Path file = write(tree);

        FraudConfig config = ConfigLoader.fromArgs(List.of("evaluate", "--config", file.toString()));
        assertEquals(1, config.weight(RuleId.AMOUNT_CLUSTERING));
        assertEquals(27, config.totalWeight());
        assertEquals(7, config.requireThreshold());

        assertEquals(28, ConfigLoader.fromArgs(List.of("score")).totalWeight(), "no flag: bundled default");
    }

    @Test
    void configFlagNeedsAnExistingFile() {
        ConfigException noValue = assertThrows(ConfigException.class,
                () -> ConfigLoader.fromArgs(List.of("score", "--config")));
        assertEquals("--config requires a file path", noValue.getMessage());

        Path missing = tmp.resolve("absent.json");
        ConfigException notFound = assertThrows(ConfigException.class,
                () -> ConfigLoader.fromArgs(List.of("score", "--config", missing.toString())));
        assertTrue(notFound.getMessage().startsWith("config file not found"), notFound.getMessage());
    }
}
