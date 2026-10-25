package com.papaya.fraud.cli;

import com.papaya.fraud.metrics.ConfusionMatrix;
import com.papaya.fraud.metrics.Evaluation;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * {@code evaluate --claims <csv> --ground-truth <csv> --config <frozen config> --out-dir <dir> [--seed <n>]}: scores
 * with the frozen config and writes scored_claims.json, metrics.json and metrics_report.md. Fails clearly when the
 * config carries no threshold T.
 */
public final class EvaluateCommand {

    static final String USAGE = "Usage: evaluate --claims <claims.csv> --ground-truth <labels.csv>"
            + " --config <frozen-config.json> --out-dir <dir> [--seed <n>]";

    private EvaluateCommand() {
    }

    public static int run(List<String> rawArgs, PrintStream out, PrintStream err) {
        Args args = new Args(rawArgs);
        Path claims;
        Path labels;
        Path config;
        Path outDir;
        try {
            claims = args.requiredPath("--claims");
            labels = args.requiredPath("--ground-truth");
            config = args.requiredPath("--config");
            outDir = args.requiredPath("--out-dir");
        } catch (Args.UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 1;
        }
        Evaluation.EvaluateOutcome outcome = Evaluation.evaluate(claims, labels, config, outDir,
                args.optional("--seed").orElse(null));
        print(out, outcome);
        return 0;
    }

    static void print(PrintStream out, Evaluation.EvaluateOutcome outcome) {
        ConfusionMatrix c = outcome.result().confusion();
        out.printf(Locale.US, "T = %d: recall %.1f%%, FPR %.1f%%, FDR %.1f%%, precision %.1f%%"
                        + " (TP %d, FP %d, FN %d, TN %d)%n", outcome.result().threshold(), c.recall() * 100,
                c.fpr() * 100, c.fdr() * 100, c.precision() * 100, c.tp(), c.fp(), c.fn(), c.tn());
        outcome.files().forEach(f -> out.println("Wrote " + f));
    }
}
