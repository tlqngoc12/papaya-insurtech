package com.papaya.fraud.io;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoredOutputWriterTest {

    private static final List<ScoredClaim> RANKED = List.of(
            new ScoredClaim(claim("CLM-00123").build(), 21, List.of(
                    new Flag(RuleId.UPCODING, 4, "Submitted amount 85,000 ..."),
                    new Flag(RuleId.AMOUNT_CLUSTERING, 2, "Amount 49,500 is \"99.0%\" ..."))),
            new ScoredClaim(claim("CLM-00124").build(), 0, List.of()));

    @Test
    void writesTheChallengeSchemaWithLfAndEmptyFlagArrays() {
        String expected = """
                [
                  {
                    "claim_id": "CLM-00123",
                    "risk_score": 21,
                    "flags": [
                      {
                        "rule": "upcoding",
                        "severity": 4,
                        "evidence": "Submitted amount 85,000 ..."
                      },
                      {
                        "rule": "amount_clustering",
                        "severity": 2,
                        "evidence": "Amount 49,500 is \\"99.0%\\" ..."
                      }
                    ]
                  },
                  {
                    "claim_id": "CLM-00124",
                    "risk_score": 0,
                    "flags": []
                  }
                ]
                """;
        byte[] bytes = ScoredOutputWriter.toBytes(RANKED);

        assertEquals(expected, new String(bytes, StandardCharsets.UTF_8));
        assertEquals('[', bytes[0], "no BOM");
        for (byte b : bytes) {
            assertTrue(b != '\r', "no CR, whatever the operating system");
        }
    }

    @Test
    void writingTwiceGivesIdenticalBytes() {
        assertArrayEquals(ScoredOutputWriter.toBytes(RANKED), ScoredOutputWriter.toBytes(RANKED));
    }

    @Test
    void emptyResultIsAnEmptyArray() {
        assertEquals("[]\n", new String(ScoredOutputWriter.toBytes(List.of()), StandardCharsets.UTF_8));
    }
}
