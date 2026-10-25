package com.papaya.fraud.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.papaya.fraud.metrics.ThresholdSelector.SweepRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdSelectorTest {

    private static SweepRow row(int t, int tp, int fp) {
        return new SweepRow(t, new ConfusionMatrix(tp, fp, 4 - tp, 10 - fp)); // 4 fraud, 10 legit
    }

    @Test
    void highestRecallAmongThresholdsWithinTheFprLimit() {
        List<SweepRow> sweep = List.of(row(1, 4, 3), row(2, 3, 1), row(3, 2, 0));
        assertEquals(2, ThresholdSelector.select(sweep, 0.20).orElseThrow().threshold(),
                "T=1 has 30% FPR; T=2 keeps 10% and recall 75%");
    }

    @Test
    void recallTieGoesToTheLowestFpr() {
        List<SweepRow> sweep = List.of(row(1, 4, 2), row(2, 4, 1), row(3, 3, 0));
        assertEquals(2, ThresholdSelector.select(sweep, 0.20).orElseThrow().threshold());
    }

    @Test
    void recallAndFprTieGoesToTheLargestThreshold() {
        List<SweepRow> sweep = List.of(row(7, 4, 1), row(8, 4, 1), row(9, 4, 1), row(10, 3, 0));
        assertEquals(9, ThresholdSelector.select(sweep, 0.20).orElseThrow().threshold());
    }

    @Test
    void theFprLimitIsInclusiveAndNothingQualifyingGivesEmpty() {
        assertEquals(5, ThresholdSelector.select(List.of(row(5, 4, 2)), 0.20).orElseThrow().threshold(),
                "exactly 20% qualifies");
        assertTrue(ThresholdSelector.select(List.of(row(1, 4, 3), row(2, 4, 5)), 0.20).isEmpty());
    }
}
