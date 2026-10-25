package com.papaya.fraud.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.Main;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.io.Json;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * tune, evaluate and all on the six-claim fixture. Scores and labels:
 *
 * <pre>
 *   CLM-00004 25 fraud (unbundling)     CLM-00006 11 fraud (mismatch)
 *   CLM-00002 18 fraud (duplicate)      CLM-00005  7 legit (band_host)
 *   CLM-00003 14 fraud (rapid)          CLM-00001  0 legit (base)
 * </pre>
 *
 * Never on the real tuning or held-out datasets (Tasks 17-18).
 */
class TuneEvaluateAllCommandTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int main(String... args) {
        out.reset();
        err.reset();
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static String resource(String name) throws URISyntaxException {
        return Path.of(TuneEvaluateAllCommandTest.class.getResource("/score/" + name).toURI()).toString();
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    /**
     * T = 1..7 also flag CLM-00005 (legit, score 7): FPR 1 of 2 = 50%, over the limit. T = 8..11 catch all four fraud
     * claims with no false positive; the largest of those ties is 11. T = 12 would miss CLM-00006 (score 11).
     */
    @Test
    void tuneSelectsTheLargestTiedThresholdAndRefusesToOverwriteWithoutForce() throws Exception {
        Path frozen = tmp.resolve("config/frozen-config.json");
        String[] tune = {"tune", "--claims", resource("claims.csv"), "--ground-truth", resource("ground_truth.csv"),
            "--out", frozen.toString()};

        assertEquals(0, main(tune), err());
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("Selected T = 11: recall 100.0%, FPR 0.0%"));
        assertEquals(11, ConfigLoader.load(frozen).requireThreshold());
        JsonNode sweep = Json.mapper().readTree(tmp.resolve("config/threshold-sweep.json").toFile());
        assertEquals(11, sweep.get("selected_threshold").asInt());
        assertEquals(100, sweep.get("rows").size());
        assertEquals(1, sweep.get("rows").get(6).get("fp").asInt(), "T = 7 flags the legit CLM-00005");

        byte[] before = Files.readAllBytes(frozen);
        assertEquals(1, main(tune));
        assertTrue(err().contains("already exists; a frozen config is not overwritten without --force"), err());
        assertArrayEquals(before, Files.readAllBytes(frozen), "the frozen config is untouched");

        String[] forced = Arrays.copyOf(tune, tune.length + 1);
        forced[tune.length] = "--force";
        assertEquals(0, main(forced), err());
    }

    @Test
    void evaluateWritesTheThreeFilesAndRefusesAConfigWithoutThreshold() throws Exception {
        Path noT = tmp.resolve("no-threshold.json");
        Files.write(noT, ConfigLoader.defaultBytes());
        Path outDir = tmp.resolve("eval");
        assertEquals(1, main("evaluate", "--claims", resource("claims.csv"), "--ground-truth",
                resource("ground_truth.csv"), "--config", noT.toString(), "--out-dir", outDir.toString()));
        assertTrue(err().contains("No threshold T in this config"), err());
        assertFalse(Files.exists(outDir), "nothing written without T");

        Path frozen = tmp.resolve("frozen.json");
        assertEquals(0, main("tune", "--claims", resource("claims.csv"), "--ground-truth", resource("ground_truth.csv"),
                "--out", frozen.toString()));
        assertEquals(0, main("evaluate", "--claims", resource("claims.csv"), "--ground-truth",
                resource("ground_truth.csv"), "--config", frozen.toString(), "--out-dir", outDir.toString()), err());
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("T = 11: recall 100.0%, FPR 0.0%"));
        for (String file : new String[] {"scored_claims.json", "metrics.json", "metrics_report.md"}) {
            assertTrue(Files.exists(outDir.resolve(file)), file);
        }
        String report = Files.readString(outDir.resolve("metrics_report.md"));
        assertTrue(report.contains("| **Fraud** | TP = 4 | FN = 0 |"), report);
        assertTrue(report.contains("| Seed | not given |"));

        byte[] scored = Files.readAllBytes(outDir.resolve("scored_claims.json"));
        assertEquals(0, main("evaluate", "--claims", resource("claims.csv"), "--ground-truth",
                resource("ground_truth.csv"), "--config", frozen.toString(), "--out-dir", outDir.toString()));
        assertArrayEquals(scored, Files.readAllBytes(outDir.resolve("scored_claims.json")));
    }

    @Test
    void evaluateWritesTheSameScoredClaimsAsScoreAndAByteIdenticalMetricsJson() throws Exception {
        Path frozen = tmp.resolve("frozen.json");
        assertEquals(0, main("tune", "--claims", resource("claims.csv"), "--ground-truth", resource("ground_truth.csv"),
                "--out", frozen.toString()));
        Path scoreOut = tmp.resolve("score-output.json");
        assertEquals(0, main("score", "--claims", resource("claims.csv"), "--config", frozen.toString(),
                "--out", scoreOut.toString()));
        Path first = tmp.resolve("eval-1");
        Path second = tmp.resolve("eval-2");
        for (Path dir : new Path[] {first, second}) {
            assertEquals(0, main("evaluate", "--claims", resource("claims.csv"), "--ground-truth",
                    resource("ground_truth.csv"), "--config", frozen.toString(), "--out-dir", dir.toString(),
                    "--seed", "7"), err());
        }

        assertArrayEquals(Files.readAllBytes(scoreOut), Files.readAllBytes(first.resolve("scored_claims.json")),
                "evaluate and score share one scoring path");
        assertArrayEquals(Files.readAllBytes(first.resolve("metrics.json")),
                Files.readAllBytes(second.resolve("metrics.json")), "metrics.json carries no per-run value");
    }

    /**
     * The same claims, ground truth and config copied to two different places: metrics.json must be byte-identical and
     * metrics_report.md identical outside its Timing section, because neither may carry an input path.
     */
    @Test
    void differentInputPathsGiveTheSameMetricsJsonAndReportOutsideTiming() throws Exception {
        Path frozen = tmp.resolve("frozen.json");
        assertEquals(0, main("tune", "--claims", resource("claims.csv"), "--ground-truth", resource("ground_truth.csv"),
                "--out", frozen.toString()));
        Path[] inputs = {tmp.resolve("copy-a"), tmp.resolve("copy-b/deeper/still")};
        Path[] outputs = {tmp.resolve("out-a"), tmp.resolve("out-b")};
        for (int i = 0; i < 2; i++) {
            Files.createDirectories(inputs[i]);
            Files.copy(Path.of(resource("claims.csv")), inputs[i].resolve("claims.csv"));
            Files.copy(Path.of(resource("ground_truth.csv")), inputs[i].resolve("ground_truth.csv"));
            Files.copy(frozen, inputs[i].resolve("frozen.json"));
            assertEquals(0, main("evaluate", "--claims", inputs[i].resolve("claims.csv").toString(), "--ground-truth",
                    inputs[i].resolve("ground_truth.csv").toString(), "--config", inputs[i].resolve("frozen.json").toString(),
                    "--out-dir", outputs[i].toString(), "--seed", "7"), err());
        }

        assertArrayEquals(Files.readAllBytes(outputs[0].resolve("metrics.json")),
                Files.readAllBytes(outputs[1].resolve("metrics.json")));
        String reportA = withoutTiming(Files.readString(outputs[0].resolve("metrics_report.md")));
        String reportB = withoutTiming(Files.readString(outputs[1].resolve("metrics_report.md")));
        assertEquals(reportA, reportB);
        assertFalse(reportA.contains(tmp.toString().replace('\\', '/')) || reportA.contains(tmp.toString()),
                "no machine path in the report");
    }

    private static String withoutTiming(String report) {
        int start = report.indexOf("## Timing");
        int end = report.indexOf("## Limitations");
        assertTrue(start > 0 && end > start, "report sections");
        return report.substring(0, start) + report.substring(end);
    }

    @Test
    void groundTruthWithAClaimIdMissingFromTheClaimsIsAnError() throws Exception {
        Path labels = tmp.resolve("labels.csv");
        Files.writeString(labels, Files.readString(Path.of(resource("ground_truth.csv")))
                + "CLM-09999,true,upcoding,,upcode_strong\n");
        Path frozen = tmp.resolve("frozen.json");
        assertEquals(1, main("tune", "--claims", resource("claims.csv"), "--ground-truth", labels.toString(),
                "--out", frozen.toString()));
        assertTrue(err().contains("ground truth has claim_id CLM-09999 that is not in the claims file"), err());
        assertFalse(Files.exists(frozen));
    }

    /**
     * Only CLM-00001 (score 0) labelled fraud: every T that keeps FPR at or below 20% (at most 1 of the 5 legit claims
     * flagged) catches nothing, so the best T has TP = 0 and tune must refuse to freeze it.
     */
    @Test
    void tuneRefusesABestThresholdThatCatchesNoFraud() throws Exception {
        Path labels = tmp.resolve("labels.csv");
        Files.writeString(labels, """
                claim_id,is_fraud,fraud_pattern,related_claim_id,scenario
                CLM-00001,true,upcoding,,upcode_mild
                CLM-00002,false,none,,base
                CLM-00003,false,none,,base
                CLM-00004,false,none,,base
                CLM-00005,false,none,,band_host
                CLM-00006,false,none,,base
                """);
        Path frozen = tmp.resolve("frozen.json");
        assertEquals(1, main("tune", "--claims", resource("claims.csv"), "--ground-truth", labels.toString(),
                "--out", frozen.toString()));
        assertTrue(err().contains("catches no fraud claim (TP = 0); nothing is frozen"), err());
        assertFalse(Files.exists(frozen));
        assertFalse(Files.exists(tmp.resolve("threshold-sweep.json")));
    }

    @Test
    void allRefusesWithoutThresholdBeforeGeneratingAndRunsWithOne() throws Exception {
        Path noT = tmp.resolve("no-threshold.json");
        Files.write(noT, ConfigLoader.defaultBytes());
        Path outDir = tmp.resolve("all");
        assertEquals(1, main("all", "--seed", "7", "--config", noT.toString(), "--out-dir", outDir.toString()));
        assertTrue(err().contains("No threshold T"), err());
        assertFalse(Files.exists(outDir), "refused before generating anything");

        // T = 7 is fake test data; seed 7 is none of the tuning, held-out or stability seeds.
        ObjectNode withT = (ObjectNode) Json.mapper().readTree(ConfigLoader.defaultBytes());
        withT.put("threshold", 7);
        Path config = tmp.resolve("with-threshold.json");
        Files.write(config, Json.prettyBytes(withT));
        assertEquals(0, main("all", "--seed", "7", "--config", config.toString(), "--out-dir", outDir.toString()),
                err());
        for (String file : new String[] {"claims.csv", "ground_truth.csv", "scored_claims.json", "metrics.json",
            "metrics_report.md"}) {
            assertTrue(Files.exists(outDir.resolve(file)), file);
        }
        assertTrue(Files.readString(outDir.resolve("metrics_report.md")).contains("| Seed | 7 |"));
    }
}
