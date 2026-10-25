package com.papaya.fraud.cli;

import com.papaya.fraud.metrics.ConfusionMatrix;
import com.papaya.fraud.metrics.Evaluation;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code tune --claims <csv> --ground-truth <csv> [--config <path>] --out <frozen-config.json> [--force]}: picks T on
 * the tuning dataset and freezes it (S3, Q10). Writes threshold-sweep.json next to the frozen config.
 */
public final class TuneCommand {

    static final String USAGE = "Usage: tune --claims <claims.csv> --ground-truth <labels.csv> [--config <config.json>]"
            + " --out <frozen-config.json> [--force]";

    private TuneCommand() {
    }

    public static int run(List<String> rawArgs, PrintStream out, PrintStream err) {
        Args args = new Args(rawArgs);
        Path claims;
        Path labels;
        Path frozen;
        try {
            claims = args.requiredPath("--claims");
            labels = args.requiredPath("--ground-truth");
            frozen = args.requiredPath("--out");
        } catch (Args.UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        }
        Path config = args.optional("--config").map(Path::of).orElse(null);
        Evaluation.TuneOutcome outcome = Evaluation.tune(claims, labels, config, frozen,
                args.raw().contains("--force"));
        ConfusionMatrix c = outcome.selected().confusion();
        out.printf(Locale.US, "Selected T = %d: recall %.1f%%, FPR %.1f%% (TP %d, FP %d, FN %d, TN %d)%n",
                outcome.selected().threshold(), c.recall() * 100, c.fpr() * 100, c.tp(), c.fp(), c.fn(), c.tn());
        out.println("Frozen config: " + outcome.frozenConfig());
        out.println("Sweep:         " + outcome.sweepFile());
        return 0;
    }
}
