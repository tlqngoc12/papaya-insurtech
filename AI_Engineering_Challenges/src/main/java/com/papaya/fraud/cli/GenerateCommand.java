package com.papaya.fraud.cli;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.generator.DatasetGenerator;
import com.papaya.fraud.generator.GeneratedDataset;
import com.papaya.fraud.generator.GeneratorConfig;
import com.papaya.fraud.generator.GeneratorConfigLoader;
import com.papaya.fraud.generator.GroundTruthRow;
import com.papaya.fraud.generator.GroundTruthWriter;
import com.papaya.fraud.io.ClaimCsvWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code generate --seed <n> --out <dir> [--generator-config <path>]}: writes {@code claims.csv} and
 * {@code ground_truth.csv}. Uses only the generator config, never the engine config.
 */
public final class GenerateCommand {

    static final String USAGE = "Usage: generate --seed <n> --out <dir> [--generator-config <generator-config.json>]";

    private GenerateCommand() {
    }

    public static int run(List<String> rawArgs, PrintStream out, PrintStream err) {
        Args args = new Args(rawArgs);
        long seed;
        Path dir;
        try {
            seed = Long.parseLong(args.optional("--seed")
                    .orElseThrow(() -> new Args.UsageException("missing required option --seed")));
            dir = args.requiredPath("--out");
        } catch (Args.UsageException | NumberFormatException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        }
        GeneratorConfig config = args.optional("--generator-config")
                .map(p -> GeneratorConfigLoader.load(Path.of(p)))
                .orElseGet(GeneratorConfigLoader::loadDefault);

        long start = System.nanoTime();
        GeneratedDataset dataset = new DatasetGenerator(config, CatalogLoader.loadDefault()).generate(seed);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create " + dir, e);
        }
        ClaimCsvWriter.write(dataset.claims(), dir.resolve("claims.csv"));
        GroundTruthWriter.write(dataset.groundTruth(), dir.resolve("ground_truth.csv"));
        long ms = (System.nanoTime() - start) / 1_000_000;

        out.printf(Locale.US, "Seed %d: %,d claims, %,d members, %,d providers in %d ms%n", seed,
                dataset.claims().size(), dataset.members().size(), dataset.providers().size(), ms);
        count(dataset.groundTruth(), GroundTruthRow::fraudPattern)
                .forEach((k, v) -> out.printf(Locale.US, "  fraud_pattern %-28s %,d%n", k, v));
        count(dataset.groundTruth(), GroundTruthRow::scenario)
                .forEach((k, v) -> out.printf(Locale.US, "  scenario      %-28s %,d%n", k, v));
        out.println("Output: " + dir.resolve("claims.csv") + ", " + dir.resolve("ground_truth.csv"));
        return 0;
    }

    private static Map<String, Long> count(List<GroundTruthRow> rows, Function<GroundTruthRow, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }
}
