package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W177 — ProfileDistance property-based tests.
 */
class ProfileDistancePropertyTest {

    @Property(tries = 50)
    void propertyL1IdenticalZero(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(ProfileDistance.l1Distance(p, p)).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyL2IdenticalZero(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(ProfileDistance.l2Distance(p, p)).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyCosineIdenticalZero(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(ProfileDistance.cosineDistance(p, p)).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyL1Symmetric(@ForAll("profiles") CognitiveGenesisProfile a,
                              @ForAll("profiles") CognitiveGenesisProfile b) {
        double dAB = ProfileDistance.l1Distance(a, b);
        double dBA = ProfileDistance.l1Distance(b, a);
        assertThat(dAB).isCloseTo(dBA, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyL2Symmetric(@ForAll("profiles") CognitiveGenesisProfile a,
                              @ForAll("profiles") CognitiveGenesisProfile b) {
        double dAB = ProfileDistance.l2Distance(a, b);
        double dBA = ProfileDistance.l2Distance(b, a);
        assertThat(dAB).isCloseTo(dBA, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyCosineSymmetric(@ForAll("profiles") CognitiveGenesisProfile a,
                                   @ForAll("profiles") CognitiveGenesisProfile b) {
        double dAB = ProfileDistance.cosineDistance(a, b);
        double dBA = ProfileDistance.cosineDistance(b, a);
        assertThat(dAB).isCloseTo(dBA, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyL1NonNegative(@ForAll("profiles") CognitiveGenesisProfile a,
                                 @ForAll("profiles") CognitiveGenesisProfile b) {
        double d = ProfileDistance.l1Distance(a, b);
        assertThat(d).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyL1BoundedByOne(@ForAll("profiles") CognitiveGenesisProfile a,
                                  @ForAll("profiles") CognitiveGenesisProfile b) {
        double d = ProfileDistance.l1Distance(a, b);
        // Each field diff is in [0, 1] (after normalization), sum/13 ≤ 1
        assertThat(d).isLessThanOrEqualTo(1.0);
    }

    @Provide
    Arbitrary<CognitiveGenesisProfile> profiles() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            Random rng = new Random(seed);
            return new CognitiveGenesisProfile(
                rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
                rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
                rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
                rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
            );
        });
    }

    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
