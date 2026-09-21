package io.matrix.research;

import io.matrix.neuron.DynamicProgramming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 434 — Coverage for DP + greedy classics.
 */
class Exp434DpGreedyTest {

    @Test
    void lisReturnsCorrectLengthForKnownInput() {
        // LIS of [10, 22, 9, 33, 21, 50, 41, 60, 80] is 6
        assertThat(DynamicProgramming.longestIncreasingSubsequence(
                new int[]{10, 22, 9, 33, 21, 50, 41, 60, 80})).isEqualTo(6);
    }

    @Test
    void lisOnSortedArray() {
        assertThat(DynamicProgramming.longestIncreasingSubsequence(
                new int[]{1, 2, 3, 4, 5})).isEqualTo(5);
    }

    @Test
    void lisOnReverseSortedIsOne() {
        assertThat(DynamicProgramming.longestIncreasingSubsequence(
                new int[]{5, 4, 3, 2, 1})).isEqualTo(1);
    }

    @Test
    void knapsackMatchesKnownAnswer() {
        // capacity 10, items: (w=1, v=1), (w=3, v=4), (w=5, v=7), (w=8, v=10)
        // Optimal: 1, 3, 5 → weights 9 ≤ 10, value 12
        // or 5 + 3 + 1 = 9 = v=12. Or skip 5 take 8+1=9 (v=11). Or take 8+3=11? no.
        // Best = 12
        int v = DynamicProgramming.knapsack(
                new int[]{1, 3, 5, 8}, new int[]{1, 4, 7, 10}, 10);
        assertThat(v).isEqualTo(12);
    }

    @Test
    void knapsackZeroCapacityIsZero() {
        assertThat(DynamicProgramming.knapsack(
                new int[]{5, 3, 1}, new int[]{10, 5, 2}, 0)).isZero();
    }

    @Test
    void subsetSumFindsExistingSum() {
        assertThat(DynamicProgramming.subsetSum(new int[]{3, 34, 4, 12, 5, 2}, 9)).isTrue();
    }

    @Test
    void subsetSumReturnsFalseForUnreachable() {
        assertThat(DynamicProgramming.subsetSum(new int[]{1, 2, 5}, 9)).isFalse();
    }

    @Test
    void activitySelectionFindsMaxNonOverlappingSet() {
        // 7 intervals; max non-overlapping = 4
        DynamicProgramming.Interval[] intervals = {
                new DynamicProgramming.Interval(1, 3),
                new DynamicProgramming.Interval(3, 5),
                new DynamicProgramming.Interval(2, 4),
                new DynamicProgramming.Interval(5, 7),
                new DynamicProgramming.Interval(4, 6),
                new DynamicProgramming.Interval(6, 9),
                new DynamicProgramming.Interval(8, 10)
        };
        // Optimal: 1-3, 3-5, 5-7, 8-10 → 4
        assertThat(DynamicProgramming.activitySelection(intervals)).isEqualTo(4);
    }
}
