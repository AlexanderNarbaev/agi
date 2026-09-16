package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W179 — RegimeTrajectoryAnalyzer property-based tests.
 */
class RegimeTrajectoryAnalyzerPropertyTest {

    @Property(tries = 50)
    void propertyRunLengthsSumEqualsTransitions(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        int[] counts = RegimeTrajectoryAnalyzer.runLengths(profiles);
        int sum = counts[0] + counts[1] + counts[2];
        int transitions = Math.max(0, profiles.size() - 1);
        // Sum of runs = number of regime changes + 1 (or 0 if empty)
        int expected = profiles.isEmpty() ? 0 : 1;
        for (int i = 1; i < profiles.size(); i++) {
            if (!profiles.get(i).regime().equals(profiles.get(i - 1).regime())) {
                expected++;
            }
        }
        assertThat(sum).isEqualTo(expected);
    }

    @Property(tries = 50)
    void propertyStabilityBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double s = RegimeTrajectoryAnalyzer.stability(profiles);
        assertThat(s).isBetween(0.0, 1.0);
    }

    @Property(tries = 30)
    void propertyConstantProfilesStabilityOne(@ForAll("anySeed") int seed,
                                              @ForAll("counts") int n) {
        if (n < 2) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(seed);
        CognitiveGenesisProfile template = randomProfile(rng);
        for (int i = 0; i < n; i++) profiles.add(template);
        assertThat(RegimeTrajectoryAnalyzer.stability(profiles)).isCloseTo(1.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyAvgRunLengthBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double avg = RegimeTrajectoryAnalyzer.avgRunLength(profiles);
        assertThat(avg).isGreaterThanOrEqualTo(0.0);
        if (!profiles.isEmpty()) {
            assertThat(avg).isLessThanOrEqualTo(profiles.size());
        }
    }

    @Property(tries = 30)
    void propertyTransitionMatrixSymmetry(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        // Not necessarily symmetric, but counts should sum correctly
        int[][] counts = RegimeTrajectoryAnalyzer.transitionCounts(profiles);
        int total = 0;
        for (int[] row : counts) {
            for (int c : row) total += c;
        }
        // Should equal number of transitions
        int expected = Math.max(0, profiles.size() - 1);
        assertThat(total).isEqualTo(expected);
    }

    @Property(tries = 50)
    void propertyStabilityForSingleProfileIsOne() {
        // Single profile has no transitions possible
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(randomProfile(new Random(42)));
        assertThat(RegimeTrajectoryAnalyzer.stability(profiles)).isEqualTo(1.0);
    }

    @Provide
    Arbitrary<List<CognitiveGenesisProfile>> profileLists() {
        return Arbitraries.integers().between(0, 16).flatMap(n ->
            Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
                List<CognitiveGenesisProfile> list = new ArrayList<>();
                Random rng = new Random(seed);
                for (int i = 0; i < n; i++) list.add(randomProfile(rng));
                return list;
            }));
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    private static CognitiveGenesisProfile randomProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
