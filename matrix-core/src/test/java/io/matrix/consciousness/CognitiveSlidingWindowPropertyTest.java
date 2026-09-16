package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W210 — CognitiveSlidingWindow property-based tests.
 */
class CognitiveSlidingWindowPropertyTest {

    @Property(tries = 30)
    void propertyWindowBounded(@ForAll("anySeed") int seed,
                                  @ForAll("addCounts") int n,
                                  @ForAll("windowSizes") int windowSize) {
        if (n < 1 || windowSize < 1) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(0, windowSize);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.windowCount()).isLessThanOrEqualTo(windowSize);
    }

    @Property(tries = 30)
    void propertySinksBounded(@ForAll("anySeed") int seed,
                                @ForAll("addCounts") int n,
                                @ForAll("sinkCounts") int sinkCount) {
        if (n < 1 || sinkCount < 0) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(sinkCount, 64);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.sinkCount()).isLessThanOrEqualTo(sinkCount);
    }

    @Property(tries = 30)
    void propertyTotalSizeBounded(@ForAll("anySeed") int seed,
                                    @ForAll("addCounts") int n,
                                    @ForAll("windowSizes") int windowSize,
                                    @ForAll("sinkCounts") int sinkCount) {
        if (n < 1 || windowSize < 1 || sinkCount < 0) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(sinkCount, windowSize);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.size()).isLessThanOrEqualTo(sinkCount + windowSize);
    }

    @Property(tries = 30)
    void propertyWindowUtilizationBounded(@ForAll("anySeed") int seed,
                                            @ForAll("addCounts") int n,
                                            @ForAll("windowSizes") int windowSize) {
        if (n < 1 || windowSize < 1) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(0, windowSize);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.windowUtilization()).isBetween(0.0, 1.0 + 1e-9);
    }

    @Property(tries = 30)
    void propertySinkUtilizationBounded(@ForAll("anySeed") int seed,
                                          @ForAll("addCounts") int n,
                                          @ForAll("sinkCounts") int sinkCount) {
        if (n < 1 || sinkCount < 0) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(sinkCount, 64);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.sinkUtilization()).isBetween(0.0, 1.0 + 1e-9);
    }

    @Property(tries = 30)
    void propertyAllEqualsSum(@ForAll("anySeed") int seed,
                                @ForAll("addCounts") int n,
                                @ForAll("windowSizes") int windowSize,
                                @ForAll("sinkCounts") int sinkCount) {
        if (n < 1 || windowSize < 1 || sinkCount < 0) return;
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(sinkCount, windowSize);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) sw.add(randomProfile(rng));
        assertThat(sw.all().size()).isEqualTo(sw.size());
        assertThat(sw.size()).isEqualTo(sw.sinkCount() + sw.windowCount());
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> addCounts() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> windowSizes() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> sinkCounts() {
        return Arbitraries.integers().between(0, 8);
    }

    private static CognitiveGenesisProfile randomProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
            rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
        );
    }
}
