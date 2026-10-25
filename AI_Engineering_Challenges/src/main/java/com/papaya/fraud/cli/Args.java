package com.papaya.fraud.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Minimal {@code --name value} option lookup for the subcommands (T2: no CLI library).
 */
final class Args {

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    private final List<String> args;

    Args(List<String> args) {
        this.args = List.copyOf(args);
    }

    List<String> raw() {
        return args;
    }

    Optional<String> optional(String name) {
        int i = args.indexOf(name);
        if (i < 0) {
            return Optional.empty();
        }
        if (i + 1 >= args.size() || args.get(i + 1).startsWith("--")) {
            throw new UsageException(name + " requires a value");
        }
        return Optional.of(args.get(i + 1));
    }

    Path requiredPath(String name) {
        return Path.of(optional(name).orElseThrow(() -> new UsageException("missing required option " + name)));
    }
}
