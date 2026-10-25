package com.papaya.fraud.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroundTruthReaderTest {

    private static final String HEADER = "claim_id,is_fraud,fraud_pattern,related_claim_id,scenario\n";

    private static Map<String, GroundTruthLabel> read(String body) {
        return GroundTruthReader.read((HEADER + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsTheFiveColumns() {
        Map<String, GroundTruthLabel> labels = read("C1,false,none,,base\nC2,true,duplicate_claim,C1,duplicate\n");
        assertEquals(new GroundTruthLabel("C2", true, "duplicate_claim", "C1", "duplicate"), labels.get("C2"));
        assertEquals(new GroundTruthLabel("C1", false, "none", "", "base"), labels.get("C1"));
    }

    @Test
    void rejectsAWrongHeaderABadBooleanAndADuplicateId() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> GroundTruthReader.read("claim_id,is_fraud\nC1,true\n".getBytes(StandardCharsets.UTF_8)))
                .getMessage().startsWith("ground truth header must be"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read("C1,yes,none,,base\n"))
                .getMessage().contains("is_fraud must be true or false"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read("C1,false,none,,base\nC1,false,none,,base\n"))
                .getMessage().contains("duplicate claim_id C1"));
    }
}
