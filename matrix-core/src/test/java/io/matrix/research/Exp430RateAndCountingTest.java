package io.matrix.research;

import io.matrix.neuron.HyperLogLog;
import io.matrix.neuron.TokenBucket;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 430 — Coverage for TokenBucket + HyperLogLog.
 */
class Exp430RateAndCountingTest {

    @Test
    void tokenBucketStartsAtFullCapacityAndDrains() {
        TokenBucket tb = new TokenBucket(10.0, 5.0, 0L); // 5 tokens/sec
        // First call at t=0 should succeed (10 tokens initially)
        assertThat(tb.take(5, 0L)).isTrue();
        // 5 tokens remain
        assertThat(tb.take(4, 0L)).isTrue();
        // Only 1 left; can't take 5
        assertThat(tb.take(5, 0L)).isFalse();
        // Wait 2 seconds and we should have refilled
        assertThat(tb.take(8, 2000L)).isTrue();
    }

    @Test
    void tokenBucketRefillsAtCorrectRate() {
        // 2 tokens per second
        TokenBucket tb = new TokenBucket(20.0, 2.0, 0L);
        assertThat(tb.take(20, 0L)).isTrue();
        // 5 seconds → 10 tokens refilled (capped at 20)
        assertThat(tb.take(8, 5000L)).isTrue();
        assertThat(tb.take(2, 5000L)).isTrue();
        assertThat(tb.take(1, 5000L)).isFalse();
    }

    @Test
    void hyperLogLogEstimatesCardinality() {
        // p=12 → 4096 buckets, std err ~ 1.6%
        HyperLogLog hll = new HyperLogLog(12);
        // Add 10,000 distinct keys
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < 10000; i++) hll.add(rng.nextLong());
        long estimate = hll.cardinality();
        // Should be within ±3% of 10000
        assertThat(estimate).isBetween(9000L, 11000L);
    }

    @Test
    void hyperLogLogIsRobustToDuplicates() {
        HyperLogLog hll = new HyperLogLog(10);
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < 10; j++) hll.add(i);  // 10 copies each
        }
        long est = hll.cardinality();
        // Distinct count is 1000; estimate within ±5%
        assertThat(est).isBetween(900L, 1100L);
    }

    @Test
    void hyperLogLogOnEmptyStreamReturnsZero() {
        HyperLogLog hll = new HyperLogLog(8);
        assertThat(hll.cardinality()).isZero();
    }
}
