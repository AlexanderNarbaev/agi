package io.matrix.research;

import io.matrix.neuron.BigArithmetic;
import io.matrix.neuron.DynamicProgramming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 435 — Coverage for BigArithmetic + DP edge cases.
 */
class Exp435BigArithmeticTest {

    @Test
    void gcdFindsGreatestCommonDivisor() {
        assertThat(BigArithmetic.gcd(48, 18)).isEqualTo(6);
        assertThat(BigArithmetic.gcd(17, 5)).isEqualTo(1);
        assertThat(BigArithmetic.gcd(100, 0)).isEqualTo(100);
    }

    @Test
    void lcmIsProductDividedByGcd() {
        assertThat(BigArithmetic.lcm(4, 6)).isEqualTo(12);
        assertThat(BigArithmetic.lcm(21, 6)).isEqualTo(42);
    }

    @Test
    void modPowMatchesExpectedValues() {
        assertThat(BigArithmetic.modPow(2, 10, 1000)).isEqualTo(24L); // 2^10=1024 mod 1000
        assertThat(BigArithmetic.modPow(3, 0, 100)).isEqualTo(1L);
        assertThat(BigArithmetic.modPow(0, 5, 100)).isEqualTo(0L);
    }

    @Test
    void modInverseRoundTrips() {
        // (7 * 43) mod 300 = 1 (since 7*43=301, 301 mod 300 = 1)
        long r1 = BigArithmetic.modInverse(7, 300);
        assertThat(r1).isEqualTo(43);
        assertThat((7L * r1) % 300).isEqualTo(1L);

        long r2 = BigArithmetic.modInverse(43, 300);
        assertThat((43L * r2) % 300).isEqualTo(1L);
    }

    @Test
    void averageHandlesOverflow() {
        assertThat(BigArithmetic.average(Long.MAX_VALUE - 1, Long.MAX_VALUE))
                .isEqualTo(Long.MAX_VALUE - 1);  // (MAX-1 + MAX) / 2 = MAX-1 (rounds down)
        assertThat(BigArithmetic.average(0, Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE / 2);
    }

    @Test
    void binomSmallMatchesPascal() {
        assertThat(BigArithmetic.binomSmall(5, 2, 1L)).isEqualTo(10L % 1);  // 10 mod 1 = 0
        assertThat(BigArithmetic.binomSmall(10, 5, 1000000000L)).isEqualTo(252L);
        assertThat(BigArithmetic.binomSmall(0, 0, 100L)).isEqualTo(1L);
        assertThat(BigArithmetic.binomSmall(5, 6, 100L)).isEqualTo(0L);
    }

    @Test
    void knapsackEdgeCases() {
        // Single item, fits: take it
        assertThat(DynamicProgramming.knapsack(new int[]{3}, new int[]{10}, 5)).isEqualTo(10);
        // Single item, doesn't fit
        assertThat(DynamicProgramming.knapsack(new int[]{10}, new int[]{5}, 3)).isZero();
        // Empty items
        assertThat(DynamicProgramming.knapsack(new int[0], new int[0], 100)).isZero();
    }

    @Test
    void lisOnEmptyArray() {
        assertThat(DynamicProgramming.longestIncreasingSubsequence(new int[0])).isZero();
        assertThat(DynamicProgramming.longestIncreasingSubsequence(new int[]{5})).isEqualTo(1);
    }
}
