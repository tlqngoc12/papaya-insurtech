package com.papaya.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.engine.FraudEngine;
import com.papaya.fraud.generator.DatasetGenerator;
import com.papaya.fraud.generator.GeneratedDataset;
import com.papaya.fraud.generator.GeneratorConfigLoader;
import com.papaya.fraud.generator.GroundTruthRow;
import com.papaya.fraud.model.ScoredClaim;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Q4: runs the engine on the generated seed-42 dataset and only REPORTS how each fraud pattern is flagged and how
 * many fraud claims match two or more rules. Nothing here may be used to tune the generator; the only assertion is
 * that every claim was scored.
 */
class GeneratedDatasetOverlapReportTest {

    @Test
    void reportOwnRuleHitsAndOverlapsPerPattern() {
        Catalog catalog = CatalogLoader.loadDefault();
        GeneratedDataset dataset = new DatasetGenerator(GeneratorConfigLoader.loadDefault(), catalog).generate(42);
        FraudEngine.Result result = new FraudEngine(ConfigLoader.loadDefault(), catalog).run(dataset.claims());
        Map<String, ScoredClaim> scored = result.ranked().stream()
                .collect(Collectors.toMap(s -> s.claim().claimId(), Function.identity()));

        Map<String, int[]> perPattern = new TreeMap<>(); // total, own rule, 2+ rules
        Map<String, Integer> otherRules = new TreeMap<>();
        for (GroundTruthRow row : dataset.groundTruth()) {
            if (!row.isFraud()) {
                continue;
            }
            Set<String> rules = scored.get(row.claimId()).flags().stream().map(f -> f.rule().id())
                    .collect(Collectors.toSet());
            int[] c = perPattern.computeIfAbsent(row.fraudPattern(), k -> new int[3]);
            c[0]++;
            if (rules.contains(row.fraudPattern())) {
                c[1]++;
            }
            if (rules.size() >= 2) {
                c[2]++;
            }
            for (String rule : rules) {
                if (!rule.equals(row.fraudPattern())) {
                    otherRules.merge(row.fraudPattern() + " -> " + rule, 1, Integer::sum);
                }
            }
        }
        System.out.println("Overlap report, seed 42 (report only, Q4):");
        System.out.printf("  %-22s %6s %10s %10s%n", "pattern", "claims", "own rule", "2+ rules");
        perPattern.forEach((pattern, c) -> System.out.printf("  %-22s %6d %10d %10d%n", pattern, c[0], c[1], c[2]));
        System.out.println("  other rules on fraud claims: " + (otherRules.isEmpty() ? "none" : otherRules));

        assertEquals(dataset.claims().size(), result.ranked().size());
    }
}
