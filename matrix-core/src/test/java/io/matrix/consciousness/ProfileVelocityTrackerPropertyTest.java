package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W169 — ProfileVelocityTracker property-based tests.
 */
class ProfileVelocityTrackerPropertyTest {

    @Property(tries = 50)
    void propertyVelocityLengthMatches(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[] v = ProfileVelocityTracker.velocity(profiles);
        if (profiles.size() < 2) {
            assertThat(v.length).isEqualTo(0);
        } else {
            assertThat(v.length).isEqualTo(profiles.size() - 1);
        }
    }

    @Property(tries = 50)
    void propertyVelocityNonNegative(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[] v = ProfileVelocityTracker.velocity(profiles);
        for (double vi : v) {
            assertThat(vi).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyIdenticalProfilesZeroVelocity(@ForAll("anySeed") int seed,
                                                 @ForAll("counts") int n) {
        if (n < 2) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(seed);
        CognitiveGenesisProfile template = randomProfile(rng);
        for (int i = 0; i < n; i++) profiles.add(template);
        double[] v = ProfileVelocityTracker.velocity(profiles);
        for (double vi : v) {
            assertThat(vi).isCloseTo(0.0, Assertions.offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyMaxVelocityIsMax(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[] v = ProfileVelocityTracker.velocity(profiles);
        double max = ProfileVelocityTracker.maxVelocity(profiles);
        if (v.length > 0) {
            for (double vi : v) {
                assertThat(max).isGreaterThanOrEqualTo(vi);
            }
        }
    }

    @Property(tries = 30)
    void propertyMeanVelocityBoundedByMax(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double mean = ProfileVelocityTracker.meanVelocity(profiles);
        double max = ProfileVelocityTracker.maxVelocity(profiles);
        if (max > 0) {
            assertThat(mean).isLessThanOrEqualTo(max);
        }
    }

    @Property(tries = 50)
    void propertyRapidlyChangingThreshold(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles,
                                            @ForAll("thresholds") double t) {
        if (t < 0) return;
        boolean isRapid = ProfileVelocityTracker.isRapidlyChanging(profiles, t);
        double max = ProfileVelocityTracker.maxVelocity(profiles);
        assertThat(isRapid).isEqualTo(max > t);
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
    Arbitrary<Double> thresholds() {
        return Arbitraries.doubles().between(0.0, 2.0);
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
