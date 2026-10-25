package com.papaya.fraud.cli;

import com.papaya.fraud.catalog.Catalog;
import com.papaya.fraud.catalog.CatalogLoader;
import com.papaya.fraud.config.ConfigLoader;
import com.papaya.fraud.config.FraudConfig;
import com.papaya.fraud.engine.FraudEngine;
import com.papaya.fraud.io.LoadResult;
import com.papaya.fraud.pipeline.ScoringPipeline;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code score --claims <csv> [--config <path>] --out <json>}: reads and validates the claims, scores and ranks
 * them, writes {@code scored_claims.json}. Needs no threshold T and never reads ground truth.
 */
public final class ScoreCommand {

    static final String USAGE = "Usage: score --claims <claims.csv> [--config <config.json>] --out <scored_claims.json>";

    private ScoreCommand() {
    }

    /** Returns the process exit code. */
    public static int run(List<String> rawArgs, PrintStream out, PrintStream err) {
        Args args = new Args(rawArgs);
        Path claimsFile;
        Path outFile;
        try {
            claimsFile = args.requiredPath("--claims");
            outFile = args.requiredPath("--out");
        } catch (Args.UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        }
        FraudConfig config = ConfigLoader.fromArgs(args.raw());
        Catalog catalog = CatalogLoader.loadDefault();

        ScoringPipeline.Run run = ScoringPipeline.run(claimsFile, config, catalog, outFile);
        LoadResult loaded = run.loaded();
        FraudEngine.Result result = run.result();

        int read = loaded.claims().size() + loaded.rejected().size();
        out.printf(Locale.US, "Claims read:      %,d (%,d rejected, %,d warnings)%n", read, loaded.rejected().size(),
                loaded.warnings().size());
        out.printf(Locale.US, "Claims scored:    %,d%n", result.ranked().size());
        long flagged = result.ranked().stream().filter(s -> !s.flags().isEmpty()).count();
        out.printf(Locale.US, "Claims flagged:   %,d%n", flagged);
        out.printf(Locale.US, "Time (ms):        load %d, score %d, write %d, total %d%n",
                run.loadMs(), run.scoreMs(), run.writeMs(), run.loadMs() + run.scoreMs() + run.writeMs());
        run.engine().coverage(result.index()).forEach((rule, coverage) ->
                out.printf(Locale.US, "Coverage %-29s %s%n", rule.id() + ":", coverage));
        out.println("Output:           " + outFile);
        loaded.rejected().forEach(r -> err.println("rejected line " + r.line() + ": " + r.reason()));
        loaded.warnings().forEach(w -> err.println("warning line " + w.line() + ": " + w.message()));
        return 0;
    }
}
