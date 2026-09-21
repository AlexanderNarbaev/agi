package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W171 — ProfileStabilityMetrics property-based tests.
 */
class ProfileStabilityMetricsPropertyTest {

    @Property(tries = 50)
    void propertyClassifyValidString(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        String c = ProfileStabilityMetrics.classify(profiles);
        assertThat(c).isIn("STATIONARY", "OSCILLATORY", "DRIFTING", "CHAOTIC", "INSUFFICIENT_DATA");
    }

    @Property(tries = 30)
    void propertyStabilityScoreBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double score = ProfileStabilityMetrics.stabilityScore(profiles);
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Property(tries = 30)
    void propertyConstantIsStationary(@ForAll("anySeed") int seed,
                                        @ForAll("lengths") int n) {
        if (n < 3) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(seed);
        CognitiveGenesisProfile template = randomProfile(rng);
        for (int i = 0; i < n; i++) profiles.add(template);
        assertThat(ProfileStabilityMetrics.classify(profiles)).isEqualTo("STATIONARY");
    }

    @Property(tries = 30)
    void propertyStabilityScoreConstantIsOne(@ForAll("anySeed") int seed,
                                               @ForAll("lengths") int n) {
        if (n < 2) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(seed);
        CognitiveGenesisProfile template = randomProfile(rng);
        for (int i = 0; i < n; i++) profiles.add(template);
        assertThat(ProfileStabilityMetrics.stabilityScore(profiles)).isCloseTo(1.0, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyStabilityScoreForEmptyIsOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        assertThat(ProfileStabilityMetrics.stabilityScore(profiles)).isEqualTo(1.0);
    }

    @Property(tries = 30)
    void propertyInsufficientDataForTinyList(@ForAll("smallLengths") int n) {
        if (n > 2) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(n);
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        assertThat(ProfileStabilityMetrics.classify(profiles)).isEqualTo("INSUFFICIENT_DATA");
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
    Arbitrary<Integer> smallLengths() {
        return Arbitraries.integers().between(0, 4);
    }

    @Provide
    Arbitrary<Integer> lengths() {
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
