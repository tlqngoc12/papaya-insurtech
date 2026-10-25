package com.papaya.fraud.metrics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.metrics.MetricsResult.GroupRecall;
import com.papaya.fraud.metrics.MetricsResult.LegitScenario;
import com.papaya.fraud.metrics.MetricsResult.PatternRecall;
import com.papaya.fraud.metrics.MetricsResult.PrecisionAtK;
import com.papaya.fraud.metrics.MetricsResult.RulePrecision;
import com.papaya.fraud.metrics.ThresholdSelector.SweepRow;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsReportWriterTest {

    private static final ReportContext CTX = new ReportContext("42", "claims.csv", "ab12", "frozen-config.json",
            "cd34", 120, 350, 110, "../../docs/limitations.md");

    private static final MetricsResult RESULT = new MetricsResult(11, new ConfusionMatrix(3, 1, 1, 5),
            List.of(new GroupRecall("reference_table", List.of("duplicate_claim"), 1, 1),
                    new GroupRecall("statistical", List.of("upcoding"), 3, 2)),
            List.of(new PatternRecall("upcoding", 2, 1, 1), new PatternRecall("phantom_billing", 0, 0, 0)),
            List.of(new RulePrecision("upcoding", 1, 1, 1), new RulePrecision("phantom_billing", 0, 0, 0)),
            List.of(new LegitScenario("none", "base", 2, 1, Map.of("rapid_resubmission", 1))),
            List.of(new PrecisionAtK(10, 4, 3)),
            Map.of("upcoding", Map.of("evaluated", 5)),
            List.of(new SweepRow(11, new ConfusionMatrix(3, 1, 1, 5))));

    @Test
    void markdownAndJsonAreByteIdenticalAcrossWritesWithLfAndNoBom() {
        for (byte[][] pair : new byte[][][] {
            {MetricsReportWriter.markdownBytes(CTX, RESULT), MetricsReportWriter.markdownBytes(CTX, RESULT)},
            {MetricsReportWriter.jsonBytes(CTX, RESULT), MetricsReportWriter.jsonBytes(CTX, RESULT)},
            {MetricsReportWriter.sweepBytes(RESULT.sweep(), 11), MetricsReportWriter.sweepBytes(RESULT.sweep(), 11)}}) {
            assertArrayEquals(pair[0], pair[1]);
            assertTrue(pair[0][0] != (byte) 0xEF, "no BOM");
            for (byte b : pair[0]) {
                assertTrue(b != '\r', "no CR");
            }
        }
    }

    @Test
    void markdownHasEverySectionAndBothFprReadings() {
        String md = new String(MetricsReportWriter.markdownBytes(CTX, RESULT), StandardCharsets.UTF_8);
        for (String expected : List.of("## Definitions", "## Confusion matrix at T = 11", "| **Fraud** | TP = 3 | FN = 1 |",
                "| Recall | 75.0% | >= 70% | PASS |", "| FPR (standard, FP / legit) | 16.7% | <= 20% | PASS |",
                "| FDR (FP / flagged) | 25.0% | <= 20% (alternative reading) | FAIL |", "## Recall by rule group (L10)",
                "## Recall by pattern", "| phantom_billing | 0 | 0 | n/a | 0 |", "## Precision by rule",
                "## Legit claims flagged, by scenario", "| none | base | 2 | 1 | rapid_resubmission 1 |",
                "| 10 | 40.0% (4) | 30.0% (3) |", "## Rule coverage", "| upcoding | evaluated 5 |",
                "## Timing (varies from run to run)",
                "| Score (index, 8 rules, rank) | 350 |", "Claims SHA-256 | `ab12`", "Seed | 42",
                "[docs/limitations.md](../../docs/limitations.md)")) {
            assertTrue(md.contains(expected), "missing: " + expected);
        }
    }

    @Test
    void jsonCarriesNoTimingSoDifferentMeasurementsGiveTheSameBytes() {
        ReportContext slow = new ReportContext("42", "claims.csv", "ab12", "frozen-config.json", "cd34", 9_999,
                8_888, 7_777, "../../docs/limitations.md");
        assertArrayEquals(MetricsReportWriter.jsonBytes(CTX, RESULT), MetricsReportWriter.jsonBytes(slow, RESULT));
        String json = new String(MetricsReportWriter.jsonBytes(CTX, RESULT), StandardCharsets.UTF_8);
        assertTrue(!json.contains("timing") && !json.contains("350"), json);
    }

    @Test
    void jsonNamesInputsBySha256OnlyAndMarkdownByFileName() {
        String json = new String(MetricsReportWriter.jsonBytes(CTX, RESULT), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"claims_sha256\": \"ab12\"") && json.contains("\"config_sha256\": \"cd34\""), json);
        assertTrue(!json.contains("claims_file") && !json.contains("config_file") && !json.contains("claims.csv"), json);
        String md = new String(MetricsReportWriter.markdownBytes(CTX, RESULT), StandardCharsets.UTF_8);
        assertTrue(md.contains("| Claims file | `claims.csv` |") && md.contains("| Config file | `frozen-config.json` |"));
    }

    @Test
    void limitationsLinkIsRelativeInsideTheProjectAndNeverAnAbsolutePath() {
        assertEquals("../../docs/limitations.md", Evaluation.limitationsLink(Path.of("reports", "heldout")));
        String outside = Evaluation.limitationsLink(Path.of(System.getProperty("java.io.tmpdir")));
        assertEquals("docs/limitations.md", outside, "outside the project: project-relative, no machine path");
    }

    @Test
    void jsonWritesNaNAsNull() {
        String json = new String(MetricsReportWriter.jsonBytes(CTX, RESULT), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"pattern\": \"phantom_billing\",\n      \"claims\": 0,\n      \"caught\": 0,\n"
                + "      \"recall\": null"), json);
        assertTrue(json.contains("\"fpr_standard_at_most_20\": true"));
        assertTrue(json.contains("\"fdr_at_most_20\": false"));
    }
}
