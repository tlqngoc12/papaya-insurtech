package com.papaya.fraud.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateCommandTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int main(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void writesClaimsAndGroundTruthThatTheScoreCommandAccepts() throws Exception {
        Path dir = tmp.resolve("tuning");
        assertEquals(0, main("generate", "--seed", "42", "--out", dir.toString()));

        List<String> claims = Files.readAllLines(dir.resolve("claims.csv"));
        List<String> truth = Files.readAllLines(dir.resolve("ground_truth.csv"));
        assertEquals("claim_id,is_fraud,fraud_pattern,related_claim_id,scenario", truth.get(0));
        assertEquals(claims.size(), truth.size());
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("Seed 42: 2,000 claims, 500 members, 50 providers"));

        out.reset();
        assertEquals(0, main("score", "--claims", dir.resolve("claims.csv").toString(),
                "--out", tmp.resolve("scored.json").toString()));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Claims read:      2,000 (0 rejected, 0 warnings)"));
    }

    @Test
    void seedIsRequiredAndMustBeANumber() {
        assertEquals(1, main("generate", "--out", tmp.toString()));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("missing required option --seed"));
        assertEquals(1, main("generate", "--seed", "abc", "--out", tmp.toString()));
    }
}
