package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W147 — PhiId property-based tests.
 */
class PhiIdPropertyTest {

    @Property(tries = 50)
    void propertyBivariateGaussianAtomIsFinite(@ForAll("sampleSizes") int n,
                                                 @ForAll("anySeed") int seed) {
        if (n < 4) return;
        Random rng = new Random(seed);
        double[][] samples = new double[n][2];
        for (int i = 0; i < n; i++) {
            samples[i][0] = rng.nextGaussian();
            samples[i][1] = rng.nextGaussian();
        }
        PhiId.PhiIdAtom atom = PhiId.bivariateGaussian(samples);
        assertThat(atom).isNotNull();
        assertThat(Double.isFinite(atom.redundancy())).isTrue();
        assertThat(Double.isFinite(atom.synergy())).isTrue();
        assertThat(Double.isFinite(atom.unqX())).isTrue();
        assertThat(Double.isFinite(atom.unqY())).isTrue();
    }

    @Property(tries = 30)
    void propertyTrivariateGaussianAtomIsFinite(@ForAll("sampleSizes") int n,
                                                  @ForAll("anySeed") int seed) {
        if (n < 4) return;
        Random rng = new Random(seed);
        double[][] samples = new double[n][3];
        for (int i = 0; i < n; i++) {
            samples[i][0] = rng.nextGaussian();
            samples[i][1] = rng.nextGaussian();
            samples[i][2] = rng.nextGaussian();
        }
        PhiId.PhiIdAtom atom = PhiId.trivariateGaussian(samples);
        assertThat(atom).isNotNull();
        assertThat(Double.isFinite(atom.redundancy())).isTrue();
        assertThat(Double.isFinite(atom.synergy())).isTrue();
    }

    @Property(tries = 50)
    void propertyBivariateRandomSeedDeterministic(@ForAll("anySeed") int seed,
                                                   @ForAll("sampleSizes") int n) {
        if (n < 4) return;
        Random rng1 = new Random(seed);
        double[][] samples1 = new double[n][2];
        for (int i = 0; i < n; i++) {
            samples1[i][0] = rng1.nextGaussian();
            samples1[i][1] = rng1.nextGaussian();
        }
        PhiId.PhiIdAtom atom1 = PhiId.bivariateGaussian(samples1);
        // Same input → same output
        PhiId.PhiIdAtom atom2 = PhiId.bivariateGaussian(samples1);
        assertThat(atom1.redundancy()).isEqualTo(atom2.redundancy());
        assertThat(atom1.synergy()).isEqualTo(atom2.synergy());
    }

    @Provide
    Arbitrary<Integer> sampleSizes() {
        return Arbitraries.integers().between(4, 64);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
