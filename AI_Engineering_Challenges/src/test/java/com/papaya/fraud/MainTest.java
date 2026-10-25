package com.papaya.fraud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void noArgumentsPrintsUsageToStderrAndFails() {
        assertEquals(Main.EXIT_USAGE, run());
        assertTrue(err().startsWith("Usage:"));
        assertEquals("", out());
    }

    @Test
    void helpPrintsUsageListingEveryCommand() {
        assertEquals(Main.EXIT_OK, run("help"));
        for (String command : Main.COMMANDS) {
            assertTrue(out().contains("  " + command + " "), "usage should list " + command);
        }
    }

    @Test
    void unknownCommandIsRejectedWithUsage() {
        assertEquals(Main.EXIT_USAGE, run("frobnicate"));
        assertTrue(err().contains("Unknown command: frobnicate"));
        assertTrue(err().contains("Usage:"));
    }

    @Test
    void knownCommandWithoutImplementationReportsNotImplemented() {
        assertEquals(Main.EXIT_NOT_IMPLEMENTED, run("score"));
        assertTrue(err().contains("'score' is not implemented yet"));
    }
}
