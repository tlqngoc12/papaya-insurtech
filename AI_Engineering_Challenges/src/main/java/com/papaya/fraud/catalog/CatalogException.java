package com.papaya.fraud.catalog;

import java.util.List;

public class CatalogException extends RuntimeException {

    private final List<String> problems;

    public CatalogException(List<String> problems) {
        super("Invalid catalog:\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public CatalogException(String message, Throwable cause) {
        super(message, cause);
        this.problems = List.of(message);
    }

    public List<String> problems() {
        return problems;
    }
}
