package com.papaya.fraud.metrics;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.FraudEngine;
import com.papaya.fraud.io.ClaimCsvReader;
import com.papaya.fraud.io.Json;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.metrics.ThresholdSelector.SweepRow;
import com.papaya.fraud.model.ScoredClaim;
import com.papaya.fraud.pipeline.ScoringPipeline;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry points of the metrics module: {@link #tune} chooses and freezes T on the tuning dataset (S3, Q10);
 * {@link #evaluate} scores a dataset with a frozen config and writes the report. This is the only place where the
 * ground truth is read.
 */
public final class Evaluation {

    public static final String SCORED_FILE = "scored_claims.json";
    public static final String METRICS_JSON = "metrics.json";
    public static final String METRICS_REPORT = "metrics_report.md";
    public static final String SWEEP_FILE = "threshold-sweep.json";

    /** What {@link #tune} chose and where it wrote it. */
    public record TuneOutcome(SweepRow selected, Path frozenConfig, Path sweepFile) {
    }

    /** What {@link #evaluate} computed and wrote. */
    public record EvaluateOutcome(MetricsResult result, ReportContext context, List<Path> files) {
    }

    private Evaluation() {
    }

    /**
     * Scores the tuning dataset with {@code config} (or the bundled default when null), sweeps T = 1..100, selects T
     * and writes {@code frozenConfig} (the config plus "threshold") and {@code threshold-sweep.json} next to it. Refuses
     * to overwrite either file unless {@code force}: a frozen config must not change silently.
     */
    public static TuneOutcome tune(Path claims, Path groundTruth, Path config, Path frozenConfig, boolean force) {
        Path sweepFile = frozenConfig.toAbsolutePath().resolveSibling(SWEEP_FILE);
        for (Path existing : List.of(frozenConfig, sweepFile)) {
            if (Files.exists(existing) && !force) {
                throw new IllegalStateException(existing + " already exists; a frozen config is not overwritten"
                        + " without --force");
            }
        }
        byte[] configBytes = config == null ? ConfigLoader.defaultBytes() : read(config);
        FraudConfig fraudConfig = config == null ? ConfigLoader.loadDefault() : ConfigLoader.load(config);
        Catalog catalog = CatalogLoader.loadDefault();
        LoadResult loaded = new ClaimCsvReader(catalog).read(claims);
        requireNoRejectedRows(loaded);
        Map<String, GroundTruthLabel> labels = GroundTruthReader.read(groundTruth);
        FraudEngine.Result result = new FraudEngine(fraudConfig, catalog).run(loaded.claims());
        checkLabels(result.ranked(), labels);

        List<SweepRow> sweep = MetricsCalculator.sweep(result.ranked(), labels);
        SweepRow selected = ThresholdSelector.select(sweep, ThresholdSelector.MAX_FPR).orElseThrow(() ->
                new IllegalStateException("no threshold T in 1..100 keeps the standard FPR at or below 20%"));
        if (selected.confusion().tp() == 0) {
            throw new IllegalStateException("the best threshold T = " + selected.threshold()
                    + " catches no fraud claim (TP = 0); nothing is frozen");
        }

        ObjectNode frozen;
        try {
            frozen = (ObjectNode) Json.mapper().readTree(configBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot parse config", e);
        }
        frozen.put("threshold", selected.threshold());
        write(frozenConfig, Json.prettyBytes(frozen));
        write(sweepFile, MetricsReportWriter.sweepBytes(sweep, selected.threshold()));
        ConfigLoader.load(frozenConfig).requireThreshold(); // the frozen file must load and carry T
        return new TuneOutcome(selected, frozenConfig, sweepFile);
    }

    /**
     * Scores {@code claims} with a frozen config (it must carry T), compares with the ground truth and writes
     * scored_claims.json, metrics.json and metrics_report.md into {@code outDir}.
     */
    public static EvaluateOutcome evaluate(Path claims, Path groundTruth, Path frozenConfig, Path outDir, String seed) {
        FraudConfig config = ConfigLoader.load(frozenConfig);
        int threshold = config.requireThreshold();
        Catalog catalog = CatalogLoader.loadDefault();
        Map<String, GroundTruthLabel> labels = GroundTruthReader.read(groundTruth);

        mkdirs(outDir);
        Path scored = outDir.resolve(SCORED_FILE);
        // The same path as the score command, so scored_claims.json is byte-identical to what score writes.
        ScoringPipeline.Run run = ScoringPipeline.run(claims, config, catalog, scored);
        requireNoRejectedRows(run.loaded());
        FraudEngine.Result result = run.result();
        checkLabels(result.ranked(), labels);

        MetricsResult metrics = MetricsCalculator.compute(result.ranked(), labels, config, threshold,
                run.engine().coverage(result.index()));
        ReportContext ctx = new ReportContext(seed == null ? "not given" : seed, display(claims),
                sha256(read(claims)), display(frozenConfig), sha256(read(frozenConfig)), run.loadMs(), run.scoreMs(),
                run.writeMs(), limitationsLink(outDir));
        Path json = outDir.resolve(METRICS_JSON);
        Path report = outDir.resolve(METRICS_REPORT);
        write(json, MetricsReportWriter.jsonBytes(ctx, metrics));
        write(report, MetricsReportWriter.markdownBytes(ctx, metrics));
        return new EvaluateOutcome(metrics, ctx, List.of(scored, json, report));
    }

    private static void requireNoRejectedRows(LoadResult loaded) {
        if (!loaded.rejected().isEmpty()) {
            throw new IllegalStateException(loaded.rejected().size() + " claim rows were rejected (first: line "
                    + loaded.rejected().get(0).line() + ", " + loaded.rejected().get(0).reason()
                    + "); metrics would not cover the whole dataset");
        }
    }

    /** Claims and ground truth must describe exactly the same claim ids, in both directions. */
    static void checkLabels(List<ScoredClaim> scored, Map<String, GroundTruthLabel> labels) {
        Set<String> ids = new HashSet<>();
        for (ScoredClaim s : scored) {
            ids.add(s.claim().claimId());
            if (!labels.containsKey(s.claim().claimId())) {
                throw new IllegalStateException("claim " + s.claim().claimId() + " has no ground truth row");
            }
        }
        for (String id : labels.keySet()) {
            if (!ids.contains(id)) {
                throw new IllegalStateException("ground truth has claim_id " + id + " that is not in the claims file");
            }
        }
    }

    /**
     * Link from the output directory to docs/limitations.md of the working directory: relative when both are on the
     * same root, absolute otherwise (e.g. an output directory on another Windows drive).
     */
    static String limitationsLink(Path outDir) {
        Path target = Path.of("docs", "limitations.md").toAbsolutePath().normalize();
        Path from = outDir.toAbsolutePath().normalize();
        Path link = from.getRoot() != null && from.getRoot().equals(target.getRoot()) ? from.relativize(target) : target;
        return link.toString().replace('\\', '/');
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private static void mkdirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + dir, e);
        }
    }
}
