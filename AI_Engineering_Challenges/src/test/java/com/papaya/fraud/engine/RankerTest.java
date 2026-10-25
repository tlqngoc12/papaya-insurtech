package com.papaya.fraud.engine;

import static com.papaya.fraud.testing.ClaimBuilder.claim;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.papaya.fraud.model.Flag;
import com.papaya.fraud.model.RuleId;
import com.papaya.fraud.model.ScoredClaim;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RankerTest {

    private static ScoredClaim scored(String id, int score, int flags, long amount) {
        List<Flag> list = new ArrayList<>();
        for (int i = 0; i < flags; i++) {
            list.add(new Flag(RuleId.values()[i], 1, "x"));
        }
        return new ScoredClaim(claim(id).withAmount(amount).build(), score, list);
    }

    private static List<String> ids(List<ScoredClaim> ranked) {
        return ranked.stream().map(s -> s.claim().claimId()).toList();
    }

    @Test
    void higherScoreComesFirst() {
        assertEquals(List.of("B", "A"), ids(Ranker.rank(List.of(scored("A", 18, 1, 9_000), scored("B", 21, 1, 100)))));
    }

    @Test
    void equalScoreMoreFlagsFirst() {
        assertEquals(List.of("B", "A"), ids(Ranker.rank(List.of(scored("A", 25, 1, 9_000), scored("B", 25, 2, 100)))));
    }

    @Test
    void equalScoreAndFlagsLargerAmountFirst() {
        assertEquals(List.of("B", "A"), ids(Ranker.rank(List.of(scored("A", 18, 1, 300), scored("B", 18, 1, 9_000)))));
    }

    @Test
    void everythingEqualClaimIdAscending() {
        assertEquals(List.of("A", "B"), ids(Ranker.rank(List.of(scored("B", 0, 0, 300), scored("A", 0, 0, 300)))));
    }

    @Test
    void rankingIsIndependentOfInputOrder() {
        List<ScoredClaim> claims = List.of(scored("A", 18, 1, 300), scored("B", 25, 2, 100), scored("C", 25, 1, 50_000),
                scored("D", 25, 1, 50_000), scored("E", 0, 0, 10));
        List<String> expected = List.of("B", "C", "D", "A", "E");
        for (long seed = 0; seed < 5; seed++) {
            List<ScoredClaim> shuffled = new ArrayList<>(claims);
            Collections.shuffle(shuffled, new Random(seed));
            assertEquals(expected, ids(Ranker.rank(shuffled)));
        }
    }
}
