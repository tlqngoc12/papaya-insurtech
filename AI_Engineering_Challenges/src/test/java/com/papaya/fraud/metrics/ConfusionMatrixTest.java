package com.papaya.fraud.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConfusionMatrixTest {

    /** Hand-computed: 4 fraud, 6 legit; 3 fraud and 1 legit flagged. */
    @Test
    void ratiosOnASmallHandComputedMatrix() {
        ConfusionMatrix c = new ConfusionMatrix(3, 1, 1, 5);

        assertEquals(4, c.flagged());
        assertEquals(4, c.fraud());
        assertEquals(6, c.legit());
        assertEquals(0.75, c.precision(), 1e-12, "3 / (3 + 1)");
        assertEquals(0.75, c.recall(), 1e-12, "3 / (3 + 1)");
        assertEquals(1.0 / 6, c.fpr(), 1e-12, "standard FPR: 1 / (1 + 5)");
        assertEquals(0.25, c.fdr(), 1e-12, "FDR: 1 / (3 + 1)");
        assertEquals(0.75, c.f1(), 1e-12, "2 x 0.75 x 0.75 / 1.5");
    }

    @Test
    void zeroDenominatorsGiveNaNNotAnException() {
        ConfusionMatrix nothingFlagged = new ConfusionMatrix(0, 0, 4, 6);
        assertTrue(Double.isNaN(nothingFlagged.precision()));
        assertTrue(Double.isNaN(nothingFlagged.fdr()));
        assertTrue(Double.isNaN(nothingFlagged.f1()));
        assertEquals(0.0, nothingFlagged.recall());
        assertEquals(0.0, nothingFlagged.fpr());
    }

    @Test
    void fprLimitIsInclusiveAndExactOnCounts() {
        assertTrue(new ConfusionMatrix(4, 2, 0, 8).fprAtMost(0.20), "2 of 10 legit is exactly 20%");
        assertFalse(new ConfusionMatrix(4, 3, 0, 7).fprAtMost(0.20), "3 of 10 is 30%");
        assertTrue(new ConfusionMatrix(0, 360, 200, 1_440).fprAtMost(0.20), "360 of 1,800 is exactly 20%");
        assertFalse(new ConfusionMatrix(0, 361, 200, 1_439).fprAtMost(0.20));
    }
}
