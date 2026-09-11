package io.matrix.research;

import io.matrix.neuron.MinHash;
import io.matrix.neuron.ReservoirSampler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 432 — Coverage for MinHash + ReservoirSampler.
 */
class Exp432MinHashReservoirTest {

    @Test
    void minHashEstimatesPerfectlyOnIdenticalSets() {
        long[] a = {1L, 2L, 3L, 4L, 5L};
        long[] b = a.clone();
        double j = MinHash.jaccard(a, b, 256, 0xCAFE);
        assertThat(j).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void minHashEstimatesZeroOnDisjointSets() {
        long[] a = {1L, 2L, 3L, 4L, 5L};
        long[] b = {6L, 7L, 8L, 9L, 10L};
        double j = MinHash.jaccard(a, b, 256, 0xCAFE);
        assertThat(j).isLessThan(0.05);
    }

    @Test
    void minHashEstimatesOverlapOnSharedSets() {
        // 80% overlap → expect j ≈ 0.8
        long[] a = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
        long[] b = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 100L, 200L};
        double j = MinHash.jaccard(a, b, 256, 0xCAFE);
        assertThat(j).isBetween(0.65, 0.95);
    }

    @Test
    void reservoirSamplerProducesSubsetOfStreamItems() {
        long[] source = new long[1000];
        for (int i = 0; i < source.length; i++) source[i] = i;
        long[] reservoir = new long[10];
        ReservoirSampler.sample(source, reservoir, new Random(0xCAFE));
        // All reservoir items should be in the source set
        Set<Long> sourceSet = new HashSet<>();
        for (long v : source) sourceSet.add(v);
        for (long r : reservoir) assertThat(sourceSet.contains(r)).isTrue();
    }

    @Test
    void reservoirSamplerCoverageIsDistributedAcrossStream() {
        long[] source = new long[100_000];
        for (int i = 0; i < source.length; i++) source[i] = i;
        long[] reservoir = new long[100];
        ReservoirSampler.sample(source, reservoir, new Random(0xCAFE));
        // Each reservoir item should be from a different "decile" of source
        int[] decilesCovered = new int[10];
        for (long r : reservoir) {
            int d = (int) (r / 10_000);
            if (d >= 10) d = 9;
            decilesCovered[d]++;
        }
        // Reservoir of 100 should hit all 10 deciles
        int covered = 0;
        for (int d : decilesCovered) if (d > 0) covered++;
        assertThat(covered).isEqualTo(10);
    }
}
