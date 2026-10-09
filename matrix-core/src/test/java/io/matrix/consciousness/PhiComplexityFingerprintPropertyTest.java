package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W197 — PhiComplexityFingerprint property-based tests.
 */
class PhiComplexityFingerprintPropertyTest {

    @Property(tries = 50)
    void propertyFingerprintLengthFour(@ForAll("profileFactories") CognitiveGenesisProfile p) {
        double[] fp = PhiComplexityFingerprint.fingerprint(p);
        assertThat(fp.length).isEqualTo(4);
    }

    @Property(tries = 50)
    void propertyFingerprintBounded(@ForAll("profileFactories") CognitiveGenesisProfile p) {
        double[] fp = PhiComplexityFingerprint.fingerprint(p);
        for (double v : fp) {
            assertThat(v).isBetween(0.0, 1.0);
        }
    }

    @Property(tries = 50)
    void propertyFingerprintIdenticalIsZero(@ForAll("profileFactories") CognitiveGenesisProfile p) {
        double[] fp1 = PhiComplexityFingerprint.fingerprint(p);
        double[] fp2 = PhiComplexityFingerprint.fingerprint(p);
        double d = PhiComplexityFingerprint.fingerprintDistance(fp1, fp2);
        assertThat(d).isCloseTo(0.0, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyFingerprintSymmetric(@ForAll("profileFactories") CognitiveGenesisProfile a,
                                       @ForAll("profileFactories") CognitiveGenesisProfile b) {
        double[] fp1 = PhiComplexityFingerprint.fingerprint(a);
        double[] fp2 = PhiComplexityFingerprint.fingerprint(b);
        double dAB = PhiComplexityFingerprint.fingerprintDistance(fp1, fp2);
        double dBA = PhiComplexityFingerprint.fingerprintDistance(fp2, fp1);
        assertThat(dAB).isCloseTo(dBA, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertySequenceFingerprintLengthFour(@ForAll("anySeed") int seed,
                                                 @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(profiles);
        assertThat(fp.length).isEqualTo(4);
    }

    @Property(tries = 30)
    void propertySequenceFingerprintBounded(@ForAll("anySeed") int seed,
                                                @ForAll("profileCounts") int n) {
        if (n < 2) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(profiles);
        // RECON-W34.9: this asserted a SINGLE bound of [0, 1.5] on all four returned
        // components, which are four different quantities with four different ranges:
        //
        //   [0] meanEntropy      Shannon entropy over 4 bins. EntropyDecomposition.entropy computes
        //                        in nats and DIVIDES BY log(2), so it returns BITS and the true
        //                        maximum is log2(4) = 2.0. (An earlier revision of this comment
        //                        claimed nats and a 1.386 ceiling; that was wrong, read from a
        //                        partial view of the method, and the corrected bound is below.)
        //   [1] meanVariance     variance of the 13 profile fields -- UNBOUNDED. The measured
        //                        1.5178 is this component, not entropy.
        //   [2] regimeComplexity CognitiveEntropyMeter.normalizedRegimeEntropy, in [0, 1]
        //   [3] composite        the mean of the three above, so also effectively unbounded
        //
        // A blanket bound across heterogeneous metrics is not a weak test, it is a meaningless
        // one: it passed while entropy was still well under its true maximum, and would fail
        // for any field set with real spread. Each component is now asserted against the range
        // its own definition implies.
        assertThat(fp[0])
                .as("entropy over 4 bins in bits, max log2(4) = 2.0")
                .isBetween(0.0, 2.0 + 1e-9);
        assertThat(fp[1]).as("variance is non-negative and unbounded").isGreaterThanOrEqualTo(0.0);
        assertThat(fp[2]).as("normalized regime entropy").isBetween(0.0, 1.0);
        assertThat(fp[3]).as("composite is non-negative").isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyEmptySequenceFingerprintIsZeros() {
        double[] fp = PhiComplexityFingerprint.sequenceFingerprint(new ArrayList<>());
        for (double v : fp) assertThat(v).isEqualTo(0.0);
    }

    @Provide
    Arbitrary<CognitiveGenesisProfile> profileFactories() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            Random rng = new Random(seed);
            return randomProfile(rng);
        });
    }

    @Provide
    Arbitrary<Integer> profileCounts() {
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
