package com.papaya.fraud;

import com.papaya.fraud.catalog.CatalogException;
import com.papaya.fraud.cli.ScoreCommand;
import com.papaya.fraud.config.ConfigException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * CLI entry point. Subcommands are wired in as their tasks land; until then they report "not implemented".
 */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 1;
    static final int EXIT_NOT_IMPLEMENTED = 2;

    static final List<String> COMMANDS = List.of("generate", "score", "tune", "evaluate", "all");

    static final String USAGE = """
            Usage: java -jar fraud-engine.jar <command> [options]

            Commands:
              generate   Generate a synthetic claims dataset and its ground truth
              score      Score claims and write the ranked output (never reads ground truth)
              tune       Select threshold T on the tuning dataset and write config/frozen-config.json
              evaluate   Compute metrics of a scored output against ground truth
              all        Run generate, score and evaluate with the frozen config
              help       Show this message
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.print(USAGE);
            return EXIT_USAGE;
        }
        String command = args[0];
        if (command.equals("help") || command.equals("--help") || command.equals("-h")) {
            out.print(USAGE);
            return EXIT_OK;
        }
        List<String> rest = List.of(args).subList(1, args.length);
        try {
            if (command.equals("score")) {
                return ScoreCommand.run(rest, out, err);
            }
        } catch (ConfigException | CatalogException | UncheckedIOException e) {
            err.println("Error: " + e.getMessage());
            return EXIT_USAGE;
        }
        if (COMMANDS.contains(command)) {
            err.println("Command '" + command + "' is not implemented yet.");
            return EXIT_NOT_IMPLEMENTED;
        }
        err.println("Unknown command: " + command);
        err.print(USAGE);
        return EXIT_USAGE;
    }
}
