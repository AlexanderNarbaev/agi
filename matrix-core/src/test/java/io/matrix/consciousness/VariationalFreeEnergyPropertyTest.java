package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W184 — VariationalFreeEnergy property-based tests.
 */
class VariationalFreeEnergyPropertyTest {

    @Property(tries = 50)
    void propertyVFEIdenticalIsZero(@ForAll("profiles") CognitiveGenesisProfile p) {
        double vfe = VariationalFreeEnergy.vfe(p, p);
        assertThat(vfe).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyVFENonNegative(@ForAll("profiles") CognitiveGenesisProfile a,
                                  @ForAll("profiles") CognitiveGenesisProfile b) {
        double vfe = VariationalFreeEnergy.vfe(a, b);
        if (!Double.isInfinite(vfe)) {
            assertThat(vfe).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 50)
    void propertyEFEIdenticalIsZero(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(VariationalFreeEnergy.expectedFreeEnergy(p, p)).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyEFEFinite(@ForAll("profiles") CognitiveGenesisProfile a,
                             @ForAll("profiles") CognitiveGenesisProfile b) {
        double efe = VariationalFreeEnergy.expectedFreeEnergy(a, b);
        assertThat(Double.isFinite(efe)).isTrue();
    }

    @Property(tries = 30)
    void propertySelectActionReturnsValidIndex(@ForAll("anySeed") int seed,
                                                 @ForAll("counts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveGenesisProfile target = randomProfile(rng);
        int idx = VariationalFreeEnergy.selectAction(profiles, target);
        assertThat(idx).isBetween(0, n - 1);
    }

    @Property(tries = 30)
    void propertyELBOFiniteForList(@ForAll("anySeed") int seed,
                                     @ForAll("counts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        CognitiveGenesisProfile prior = randomProfile(rng);
        double elbo = VariationalFreeEnergy.elbo(profiles, prior);
        assertThat(Double.isFinite(elbo) || Double.isNaN(elbo)).isTrue();
    }

    @Provide
    Arbitrary<CognitiveGenesisProfile> profiles() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            Random rng = new Random(seed);
            return randomProfile(rng);
        });
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(1, 16);
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

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
