package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W201 — CognitiveAutocorrelationMatrix property-based tests.
 */
class CognitiveAutocorrelationMatrixPropertyTest {

    @Property(tries = 30)
    void propertyMatrixDimensionsCorrect(@ForAll("anySeed") int seed,
                                           @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        double[][] m = CognitiveAutocorrelationMatrix.compute(profiles);
        assertThat(m.length).isEqualTo(13);
        for (double[] row : m) {
            assertThat(row.length).isEqualTo(13);
        }
    }

    @Property(tries = 30)
    void propertyEmptyProfileListReturnsZeroMatrix() {
        double[][] m = CognitiveAutocorrelationMatrix.compute(new ArrayList<>());
        for (double[] row : m) {
            for (double v : row) {
                assertThat(v).isEqualTo(0.0);
            }
        }
    }

    @Property(tries = 30)
    void propertyHighlyCorrelatedThreshold(@ForAll("anySeed") int seed,
                                              @ForAll("profileCounts") int n,
                                              @ForAll("thresholds") double threshold) {
        if (n < 2) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        var pairs = CognitiveAutocorrelationMatrix.highlyCorrelated(profiles, threshold);
        for (var p : pairs) {
            assertThat(Math.abs(p.correlation())).isGreaterThan(threshold);
        }
    }

    @Property(tries = 30)
    void propertyAverageOffDiagonalBounded(@ForAll("anySeed") int seed,
                                              @ForAll("profileCounts") int n) {
        if (n < 2) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        double avg = CognitiveAutocorrelationMatrix.averageOffDiagonal(profiles);
        assertThat(Math.abs(avg)).isLessThanOrEqualTo(1.0);
    }

    @Property(tries = 30)
    void propertyHighlyCorrelatedSortsByMagnitude(@ForAll("anySeed") int seed,
                                                    @ForAll("profileCounts") int n,
                                                    @ForAll("thresholds") double threshold) {
        if (n < 3) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        var pairs = CognitiveAutocorrelationMatrix.highlyCorrelated(profiles, threshold);
        for (int i = 1; i < pairs.size(); i++) {
            assertThat(Math.abs(pairs.get(i).correlation())).isLessThanOrEqualTo(Math.abs(pairs.get(i - 1).correlation()));
        }
    }

    @Provide
    Arbitrary<Integer> profileCounts() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Double> thresholds() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
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
