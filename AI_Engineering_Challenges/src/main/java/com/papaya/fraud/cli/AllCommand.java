package com.papaya.fraud.cli;

import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.generator.DatasetFiles;
import com.papaya.fraud.generator.DatasetGenerator;
import com.papaya.fraud.generator.GeneratorConfigLoader;
import com.papaya.fraud.metrics.Evaluation;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code all --seed <n> --config <frozen config> --out-dir <dir>}: generate, then evaluate, with a frozen config only
 * (Q10). Checks for T before generating anything.
 */
public final class AllCommand {

    static final String USAGE = "Usage: all --seed <n> --config <frozen-config.json> --out-dir <dir>";

    private AllCommand() {
    }

    public static int run(List<String> rawArgs, PrintStream out, PrintStream err) {
        Args args = new Args(rawArgs);
        long seed;
        Path config;
        Path outDir;
        try {
            seed = Long.parseLong(args.optional("--seed")
                    .orElseThrow(() -> new Args.UsageException("missing required option --seed")));
            config = args.requiredPath("--config");
            outDir = args.requiredPath("--out-dir");
        } catch (Args.UsageException | NumberFormatException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        }
        ConfigLoader.load(config).requireThreshold(); // refuse before writing anything

        DatasetFiles.Written dataset = DatasetFiles.write(
                new DatasetGenerator(GeneratorConfigLoader.loadDefault(), CatalogLoader.loadDefault()).generate(seed),
                outDir);
        out.println("Generated seed " + seed + ": " + dataset.claims());
        Evaluation.EvaluateOutcome outcome = Evaluation.evaluate(dataset.claims(), dataset.labels(), config, outDir,
                Long.toString(seed));
        EvaluateCommand.print(out, outcome);
        return 0;
    }
}
