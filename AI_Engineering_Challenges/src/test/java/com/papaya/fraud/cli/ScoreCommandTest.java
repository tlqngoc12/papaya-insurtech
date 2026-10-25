package com.papaya.fraud.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.Main;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end through {@code Main}: six hand-built claims, each triggering a different combination of rules, scored
 * into a file that must match the hand-written expected output byte for byte.
 */
class ScoreCommandTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int main(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(ScoreCommandTest.class.getResource("/score/" + name).toURI());
    }

    @Test
    void sixClaimFileProducesExactlyTheExpectedRankedOutput() throws Exception {
        Path output = tmp.resolve("scored_claims.json");

        assertEquals(0, main("score", "--claims", resource("claims.csv").toString(), "--out", output.toString()));

        byte[] expected;
        try (InputStream in = ScoreCommandTest.class.getResourceAsStream("/score/expected-scored_claims.json")) {
            expected = in.readAllBytes();
        }
        assertEquals(new String(expected, StandardCharsets.UTF_8), Files.readString(output));
        String stdout = out.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("Claims read:      6 (0 rejected, 0 warnings)"), stdout);
        assertTrue(stdout.contains("Claims scored:    6"), stdout);
        assertTrue(stdout.contains("Claims flagged:   5"), stdout);
        assertTrue(stdout.contains("Coverage upcoding:"), stdout);
        assertTrue(stdout.contains("Time (ms):        load "), stdout);
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void reportsRejectedRowsAndWarnings() throws IOException {
        Path claims = tmp.resolve("claims.csv");
        Files.writeString(claims, Files.readString(resourceUnchecked())
                + "CLM-00007,M-0005,PRV-03,Gamma Clinic,2024-03-16,OUTPATIENT,J06.9,P-1001,0,true\n"
                + "CLM-00008,M-0006,PRV-03,Gamma Clinic,2024-03-16,OUTPATIENT,J06.9,P-1001,300,false\n");

        assertEquals(0, main("score", "--claims", claims.toString(), "--out", tmp.resolve("o.json").toString()));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Claims read:      8 (1 rejected, 1 warnings)"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("rejected line 8: submitted_amount 0 must be > 0"));
    }

    @Test
    void missingOptionPrintsUsage() {
        assertEquals(1, main("score", "--out", tmp.resolve("o.json").toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("missing required option --claims"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains(ScoreCommand.USAGE));
    }

    private static Path resourceUnchecked() {
        try {
            return resource("claims.csv");
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
