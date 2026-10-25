package com.papaya.fraud.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads main source files so architecture rules can be checked without extra libraries. */
final class SourceScan {

    static final Path MAIN = Path.of("src", "main", "java", "com", "papaya", "fraud");

    private SourceScan() {
    }

    /** Java files under the given packages (relative to com.papaya.fraud) or single files. */
    static List<Path> files(String... packagesOrFiles) {
        List<Path> files = new ArrayList<>();
        for (String entry : packagesOrFiles) {
            Path path = MAIN.resolve(entry);
            if (Files.isRegularFile(path)) {
                files.add(path);
                continue;
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException("no such package or file: " + path.toAbsolutePath());
            }
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    /** "file: token" for every file that contains one of the tokens. */
    static List<String> occurrences(List<Path> files, String... tokens) {
        List<String> found = new ArrayList<>();
        for (Path file : files) {
            String text;
            try {
                text = Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (String token : tokens) {
                if (text.contains(token)) {
                    found.add(MAIN.relativize(file) + ": " + token);
                }
            }
        }
        return found;
    }
}
