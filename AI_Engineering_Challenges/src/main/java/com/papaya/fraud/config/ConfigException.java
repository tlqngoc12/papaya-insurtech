package com.papaya.fraud.config;

import java.util.List;

public class ConfigException extends RuntimeException {

    private final List<String> problems;

    public ConfigException(String source, List<String> problems) {
        super("Invalid config " + source + ":\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public ConfigException(String message) {
        this(message, (Throwable) null);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
        this.problems = List.of(message);
    }

    public List<String> problems() {
        return problems;
    }
}
