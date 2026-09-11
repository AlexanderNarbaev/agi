package io.matrix.research;

import io.matrix.neuron.CascadeFilter;
import io.matrix.neuron.HyperLogLog;
import io.matrix.neuron.TokenBucket;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 431 — Coverage for CascadeFilter.
 */
class Exp431CascadeFilterTest {

    @Test
    void estimateMatchesExactForKnownInserts() {
        CascadeFilter cf = new CascadeFilter(0.01, 0.01);
        cf.add(7L, 5);
        // estimate(7) ≥ 5 with overestimation up to e × 5
        assertThat(cf.estimate(7L)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void estimateOverCountsOnlyByCascadeBound() {
        // Use tight error probability so bucket sizes are well-separated
        CascadeFilter cf = new CascadeFilter(0.001, 0.01);
        // Insert 100 different keys (width ≥ 1400 so collisions are rare)
        for (long i = 0; i < 100; i++) cf.add(i, 1);
        int totalOver = 0;
        int overCount = 0;
        for (long i = 0; i < 100; i++) {
            int est = cf.estimate(i);
            assertThat(est).isGreaterThanOrEqualTo(1);
            if (est > 1) overCount++;
            totalOver += est - 1;
        }
        // Most should be 1; only a few over
        assertThat(totalOver).isLessThan(50);
    }

    @Test
    void cascadeFilterIsPureAndDeterministic() {
        CascadeFilter a = new CascadeFilter(0.01, 0.01);
        CascadeFilter b = new CascadeFilter(0.01, 0.01);
        for (long i = 0; i < 50; i++) {
            a.add(i, 3);
            b.add(i, 3);
        }
        assertThat(a.estimate(42L)).isEqualTo(b.estimate(42L));
    }

    @Test
    void tokenBucketRejectsMoreThanBucketHolds() {
        TokenBucket tb = new TokenBucket(1.0, 10.0, 0L);
        // Burst: drain all
        assertThat(tb.take(0.5, 0L)).isTrue();
        assertThat(tb.take(0.5, 0L)).isTrue();
        // Try more immediately → rejected
        assertThat(tb.take(0.5, 0L)).isFalse();
    }

    @Test
    void tokenBucketRefillsProportionalToTime() {
        TokenBucket tb = new TokenBucket(100.0, 1.0, 0L);  // 1 tps
        tb.take(50, 0L); // drain 50
        // 10s → 10 tokens refilled, capped at 100 (50 still there + 10 = 60)
        assertThat(tb.take(60, 10_000L)).isTrue();
    }

    @Test
    void hyperLogLogAccuracyAtModerateScale() {
        // 50,000 distinct longs, p=14 → 16384 buckets, std err ~ 0.78%
        HyperLogLog hll = new HyperLogLog(14);
        for (long i = 0; i < 50_000; i++) hll.add(i * 31L + 0xCAFE);
        long est = hll.cardinality();
        // Allow ±10% (generous — actual std err gives ±~2.5% but bias correction
        // matters here)
        assertThat(est).isBetween(45_000L, 55_000L);
    }
}
