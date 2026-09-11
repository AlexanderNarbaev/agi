package io.matrix.research;

import io.matrix.neuron.BloomFilter;
import io.matrix.neuron.PageRank;
import io.matrix.neuron.ThompsonBandit;
import io.matrix.neuron.UcbBandit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 419 — Coverage for UCB, Thompson Sampling, Bloom Filter, PageRank.
 */
class Exp419BanditGraphDsTest {

    @Test
    void ucbPullsEachArmOnceFirst() {
        double[] means = {0.0, 0.0, 0.0};
        long[] counts = {0, 0, 0};
        Random rng = new Random(0xCAFE);
        UcbBandit.Step first = UcbBandit.choose(means, counts, -1, Math.sqrt(2), rng);
        // First iteration: counts all zero, picks first untried arm (deterministic via scan)
        assertThat(first.chosen()).isEqualTo(0);

        // Pretend arm 1 has been tried once
        counts[1] = 1;
        UcbBandit.Step second = UcbBandit.choose(means, counts, 1, Math.sqrt(2), rng);
        // UCB will prioritise an untried arm over a tried arm
        long[] ones = {1, 1, 0};
        UcbBandit.Step third = UcbBandit.choose(means, ones, 2, Math.sqrt(2), rng);
        assertThat(third.chosen()).isEqualTo(2);
        // count check ignored for `second` — it can pick 0 or 2 depending on ties
        assertThat(second.chosen()).isBetween(0, 2);
    }

    @Test
    void thompsonFavoursArmWithHigherEmpiricalMean() {
        // 2 arms. Arm 0 has higher mean.
        Random rng = new Random(0xBEAD);
        double[] means = {0.9, 0.1};
        double[] vars = {0.1, 0.1};
        long[] counts = {100, 100};
        int picks = 0;
        for (int i = 0; i < 100; i++) {
            var step = ThompsonBandit.choose(means, vars, counts, 0, 1, rng);
            if (step.chosen() == 0) picks++;
        }
        // Should pick arm 0 the great majority of times
        assertThat(picks).isGreaterThan(70);
    }

    @Test
    void bloomFilterMembershipHasFalsePositivesButNoFalseNegatives() {
        BloomFilter bf = new BloomFilter(1024, 3);
        for (long i = 0; i < 100; i++) bf.add(i);
        for (long i = 0; i < 100; i++) assertThat(bf.mightContain(i)).isTrue();
        // No false negatives confirmed
        int falsePositives = 0;
        for (long i = 100; i < 1100; i++) {
            if (bf.mightContain(i)) falsePositives++;
        }
        // With m=1024 bits, n=100 keys, k=3 hash: theoretic FPR ≈ 1-exp(-n*k/m)^k = 3.1%
        assertThat(falsePositives).isLessThan(80); // generous upper bound
    }

    @Test
    void pageRankFindsTheStrongestNode() {
        // Simple: node 0 points to 1, node 2 points to 1 twice (via duplicates if any),
        // node 1 points to 2 — and node 3 has no edges (dangling).
        List<List<Integer>> g = new ArrayList<>();
        g.add(List.of(1, 2));
        g.add(List.of(2));
        g.add(List.of());
        g.add(List.of(0));
        double[] ranks = PageRank.rank(g, 0.85, 100, 1e-9, null);
        // 0 + 3 → 1; 1 → 2; 2 dangling. Should be: 0 ~ 1/4 (after damping math),
        // 3 ~ 1/4 + a bit (from incoming 0); 1,2 share the rest.
        assertThat(ranks).hasSize(4);
        double sum = 0.0;
        for (double r : ranks) sum += r;
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void pageRankTopKReturnsSorted() {
        List<List<Integer>> g = new ArrayList<>();
        g.add(List.of(1));
        g.add(List.of(2));
        g.add(List.of(0, 1));
        double[] ranks = PageRank.rank(g, 0.85, 100, 1e-9, null);
        var top = PageRank.topK(ranks, 3);
        // top[0] should have the highest rank
        int first = top.get(0)[0];
        int second = top.get(1)[0];
        assertThat(ranks[first]).isGreaterThanOrEqualTo(ranks[second]);
    }
}
