package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W130 — Analogical consistency property-based tests.
 *
 * <p>Property-based verification of W105 AnalogicalConsistency metrics.
 * Uses jqwik @Property annotations for Quarkus XML verification.
 */
class AnalogicalConsistencyPropertyTest {

    @Property(tries = 100)
    void propertyBitSimilarityBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double sim = AnalogicalConsistency.bitSimilarity(pair[0], pair[1]);
        assertThat(sim).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyBitSimilaritySymmetric(@ForAll("equalLengthTrajectories") long[][] pair) {
        double simAB = AnalogicalConsistency.bitSimilarity(pair[0], pair[1]);
        double simBA = AnalogicalConsistency.bitSimilarity(pair[1], pair[0]);
        assertThat(simAB).isEqualTo(simBA);
    }

    @Property(tries = 100)
    void propertyBitSimilarityReflexive(@ForAll("trajectories") long[] a) {
        double sim = AnalogicalConsistency.bitSimilarity(a, a);
        assertThat(sim).isEqualTo(1.0);
    }

    @Property(tries = 100)
    void propertyStructuralSimilarityBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double sim = AnalogicalConsistency.structuralSimilarity(pair[0], pair[1]);
        assertThat(sim).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyStructuralSimilarityReflexive(@ForAll("trajectories") long[] a) {
        double sim = AnalogicalConsistency.structuralSimilarity(a, a);
        assertThat(sim).isEqualTo(1.0);
    }

    @Property(tries = 50)
    void propertyCompressionAnalogyIdenticalCompresses(@ForAll("trajectories") long[] a) {
        double ratio = AnalogicalConsistency.compressionAnalogy(a, a);
        // Identical trajectories share full structure
        assertThat(ratio).isLessThan(1.0);
    }

    @Property(tries = 50)
    void propertyCompressionAnalogyRandomInRange(@ForAll("anySeed") int seed) {
        Random rng = new Random(seed);
        long[] a = new long[16];
        long[] b = new long[16];
        for (int i = 0; i < 16; i++) {
            a[i] = rng.nextLong();
            b[i] = rng.nextLong();
        }
        double ratio = AnalogicalConsistency.compressionAnalogy(a, b);
        // Random independent sequences: ratio near 1.0
        assertThat(ratio).isBetween(0.5, 1.5);
    }

    @Property(tries = 50)
    void propertyIdenticalHasLowCompressionRatio(@ForAll("trajectoryLength") int length) {
        long[] a = new long[length];
        long[] b = a.clone();
        double ratio = AnalogicalConsistency.compressionAnalogy(a, b);
        // Identical sequences compress better than separate
        assertThat(ratio).isLessThan(1.0);
    }

    @Provide
    Arbitrary<long[]> trajectories() {
        return Arbitraries.integers().between(4, 32).flatMap(t ->
            Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<long[][]> equalLengthTrajectories() {
        return Arbitraries.integers().between(4, 32).flatMap(t ->
            Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t).flatMap(a ->
                Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t)
                    .map(b -> new long[][] { a, b })));
    }

    @Provide
    Arbitrary<Integer> trajectoryLength() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
