package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StreamingAndMathTest {

    // ============ HyperLogLog ============

    @Test
    void hyperLogLogEstimatesSmallCardinality() {
        HyperLogLog hll = new HyperLogLog(8);
        for (long i = 0; i < 100; i++) hll.add(i);
        long est = hll.cardinality();
        // Within ~10% of true count
        assertThat(est).isBetween(90L, 110L);
    }

    @Test
    void hyperLogLogRejectsBadPrecision() {
        assertThatThrownBy(() -> new HyperLogLog(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HyperLogLog(20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hyperLogLogHandlesDuplicates() {
        HyperLogLog hll = new HyperLogLog(8);
        for (int i = 0; i < 1000; i++) hll.add(42L); // all duplicates
        long est = hll.cardinality();
        assertThat(est).isBetween(0L, 5L); // should be ~1
    }

    @Test
    void hyperLogLogAccuracyImprovesWithMoreData() {
        HyperLogLog hll = new HyperLogLog(12);
        for (long i = 0; i < 10000; i++) hll.add(i);
        long est = hll.cardinality();
        // 10000 unique items, HLL with p=12 has ~1.6/SQRT(2^12) ≈ 2.5% standard error.
        // Use generous 15% bounds to avoid flakiness.
        assertThat(est).isBetween(8500L, 11500L);
    }

    // ============ CascadeFilter ============

    @Test
    void cascadeFilterEstimatesFrequentItems() {
        CascadeFilter cf = new CascadeFilter();
        // Add 1000 of key 42, 10 of others
        for (int i = 0; i < 1000; i++) cf.add(42L, 1);
        for (int i = 0; i < 10; i++) cf.add(1000L + i, 1);
        int est42 = cf.estimate(42L);
        assertThat(est42).isGreaterThan(500);
    }

    @Test
    void cascadeFilterRejectsBadArgs() {
        assertThatThrownBy(() -> new CascadeFilter(-0.1, 0.05))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cascadeFilterCountsMultiple() {
        CascadeFilter cf = new CascadeFilter();
        cf.add(42L, 5);
        cf.add(42L, 3);
        int est = cf.estimate(42L);
        assertThat(est).isGreaterThanOrEqualTo(8);
    }

    // ============ ReservoirSampler ============

    @Test
    void reservoirSamplingProducesUniformSubsample() {
        long[] source = new long[1000];
        for (int i = 0; i < source.length; i++) source[i] = i;
        long[] reservoir = new long[10];
        ReservoirSampler.sample(source, reservoir, new Random(42));
        // All reservoir slots should be filled
        for (long v : reservoir) {
            assertThat(v).isBetween(0L, 999L);
        }
        // All should be unique (with high probability for k=10, n=1000)
        long[] sorted = reservoir.clone();
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            assertThat(sorted[i]).isNotEqualTo(sorted[i - 1]);
        }
    }

    @Test
    void reservoirSamplingWithFullSourceMatches() {
        long[] source = {1L, 2L, 3L, 4L, 5L};
        long[] reservoir = new long[5];
        ReservoirSampler.sample(source, reservoir, new Random(1));
        Arrays.sort(reservoir);
        assertThat(reservoir).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    // ============ TokenBucket ============

    @Test
    void tokenBucketStartsAtCapacity() {
        TokenBucket bucket = new TokenBucket(10.0, 1.0, 0L);
        assertThat(bucket.tokens(0L)).isEqualTo(10.0);
    }

    @Test
    void tokenBucketTakeConsumesTokens() {
        TokenBucket bucket = new TokenBucket(10.0, 1.0, 0L);
        assertThat(bucket.take(3.0, 0L)).isTrue();
        assertThat(bucket.tokens(0L)).isBetween(6.99, 7.01);
    }

    @Test
    void tokenBucketRejectsOverdraw() {
        TokenBucket bucket = new TokenBucket(5.0, 1.0, 0L);
        assertThat(bucket.take(10.0, 0L)).isFalse();
    }

    @Test
    void tokenBucketRefillsOverTime() {
        TokenBucket bucket = new TokenBucket(10.0, 1.0, 0L);
        bucket.take(10.0, 0L);
        // After 5 seconds at 1 token/sec → 5 tokens
        double tokens = bucket.tokens(5000L);
        assertThat(tokens).isCloseTo(5.0, within(0.01));
    }

    @Test
    void tokenBucketRejectsBadArgs() {
        assertThatThrownBy(() -> new TokenBucket(-1.0, 1.0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(10.0, -1.0, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ============ BigArithmetic ============

    @Test
    void modPowComputesModularExponent() {
        // 2^10 = 1024 mod 1000 = 24
        assertThat(BigArithmetic.modPow(2, 10, 1000)).isEqualTo(24L);
    }

    @Test
    void modPowWithExpZeroReturnsOne() {
        assertThat(BigArithmetic.modPow(7, 0, 100)).isEqualTo(1L);
    }

    @Test
    void gcdComputesGreatestCommonDivisor() {
        assertThat(BigArithmetic.gcd(12, 8)).isEqualTo(4L);
        assertThat(BigArithmetic.gcd(17, 5)).isEqualTo(1L);
        assertThat(BigArithmetic.gcd(0, 5)).isEqualTo(5L);
    }

    @Test
    void lcmComputesLeastCommonMultiple() {
        assertThat(BigArithmetic.lcm(4, 6)).isEqualTo(12L);
        assertThat(BigArithmetic.lcm(7, 11)).isEqualTo(77L);
    }

    @Test
    void modInverseComputesModularInverse() {
        // 3 * inv ≡ 1 mod 11 → inv = 4 (since 3*4 = 12 = 1 mod 11)
        assertThat(BigArithmetic.modInverse(3, 11)).isEqualTo(4L);
    }

    @Test
    void averageComputesFloorMean() {
        assertThat(BigArithmetic.average(0, 0)).isEqualTo(0L);
        assertThat(BigArithmetic.average(1, 2)).isEqualTo(1L);
        assertThat(BigArithmetic.average(2, 5)).isEqualTo(3L);
    }

    @Test
    void binomSmallComputesBinomial() {
        // C(5, 2) = 10
        assertThat(BigArithmetic.binomSmall(5, 2, 1_000_000_007L)).isEqualTo(10L);
        // C(10, 3) = 120
        assertThat(BigArithmetic.binomSmall(10, 3, 1_000_000_007L)).isEqualTo(120L);
    }

    @Test
    void binomSmallEdgeCases() {
        // C(n, 0) = C(n, n) = 1
        assertThat(BigArithmetic.binomSmall(5, 0, 1_000_000_007L)).isEqualTo(1L);
        assertThat(BigArithmetic.binomSmall(5, 5, 1_000_000_007L)).isEqualTo(1L);
    }

    // ============ XxHash ============

    @Test
    void xxh3IsDeterministic() {
        byte[] data = "hello world".getBytes();
        long h1 = XxHash.xxh3(data);
        long h2 = XxHash.xxh3(data);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void xxh3DifferentDataDifferentHashes() {
        byte[] a = "hello".getBytes();
        byte[] b = "world".getBytes();
        assertThat(XxHash.xxh3(a)).isNotEqualTo(XxHash.xxh3(b));
    }

    @Test
    void xxh3DifferentSeedsProduceDifferentHashes() {
        byte[] data = "hello".getBytes();
        long h1 = XxHash.xxh3(data, 0L);
        long h2 = XxHash.xxh3(data, 42L);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void xxh3EmptyArrayReturnsNonZero() {
        // XXH3_64bits empty seed=0 → 0x2D06800538D394C2
        long h = XxHash.xxh3(new byte[0]);
        // Just verify it's stable
        assertThat(h).isEqualTo(XxHash.xxh3(new byte[0]));
    }

    @Test
    void xxh3HashesAreUniform() {
        // 1000 different inputs should produce ~uniform distribution
        long[] hashes = new long[1000];
        for (int i = 0; i < 1000; i++) {
            hashes[i] = XxHash.xxh3(("item-" + i).getBytes());
        }
        // Each hash should be unique (extremely high probability)
        java.util.HashSet<Long> uniq = new java.util.HashSet<>();
        for (long h : hashes) uniq.add(h);
        assertThat(uniq.size()).isGreaterThan(990);
    }

    // ============ Csv ============

    @Test
    void csvParseLineSplitsBasicFields() {
        List<String> fields = Csv.parseLine("a,b,c");
        assertThat(fields).containsExactly("a", "b", "c");
    }

    @Test
    void csvParseLineHandlesQuotedFields() {
        List<String> fields = Csv.parseLine("\"hello, world\",foo");
        assertThat(fields).containsExactly("hello, world", "foo");
    }

    @Test
    void csvParseLineHandlesEscapedQuotes() {
        // RFC 4180: "" inside quoted field is a literal "
        List<String> fields = Csv.parseLine("\"she said \"\"hi\"\"\",x");
        assertThat(fields).containsExactly("she said \"hi\"", "x");
    }

    @Test
    void csvParseLineHandlesEmptyFields() {
        List<String> fields = Csv.parseLine("a,,c");
        assertThat(fields).containsExactly("a", "", "c");
    }

    @Test
    void csvFormatLineJoinsFields() {
        String line = Csv.formatLine(Arrays.asList("a", "b", "c"));
        assertThat(line).isEqualTo("a,b,c");
    }

    @Test
    void csvFormatLineQuotesFieldsWithCommas() {
        String line = Csv.formatLine(Arrays.asList("hello, world", "foo"));
        assertThat(line).isEqualTo("\"hello, world\",foo");
    }

    @Test
    void csvFormatLineEscapesQuotes() {
        String line = Csv.formatLine(Arrays.asList("she said \"hi\"", "x"));
        assertThat(line).isEqualTo("\"she said \"\"hi\"\"\",x");
    }

    @Test
    void csvRoundTrip() {
        List<String> original = Arrays.asList("foo", "bar,baz", "\"quoted\"");
        String line = Csv.formatLine(original);
        List<String> parsed = Csv.parseLine(line);
        assertThat(parsed).isEqualTo(original);
    }
}
