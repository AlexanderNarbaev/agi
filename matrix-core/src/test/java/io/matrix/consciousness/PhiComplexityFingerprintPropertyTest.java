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
        for (double v : fp) {
            assertThat(v).isBetween(0.0, 1.5);
        }
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
