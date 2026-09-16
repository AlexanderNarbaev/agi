package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W131 — Conceptual exclusion property-based tests.
 *
 * <p>Property-based verification of W106 ConceptualExclusion metrics.
 */
class ConceptualExclusionPropertyTest {

    @Property(tries = 100)
    void propertyJaccardExclusionBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double excl = ConceptualExclusion.jaccardExclusion(pair[0], pair[1]);
        assertThat(excl).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyJaccardExclusionReflexive(@ForAll("trajectories") long[] a) {
        double excl = ConceptualExclusion.jaccardExclusion(a, a);
        // Identical → Jaccard=1 → exclusion=0
        assertThat(excl).isEqualTo(0.0);
    }

    @Property(tries = 100)
    void propertyBitMaskExclusionBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double excl = ConceptualExclusion.bitMaskExclusion(pair[0], pair[1]);
        assertThat(excl).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyBitMaskExclusionReflexive(@ForAll("trajectories") long[] a) {
        double excl = ConceptualExclusion.bitMaskExclusion(a, a);
        assertThat(excl).isEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyDistributionalExclusionBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double excl = ConceptualExclusion.distributionalExclusion(pair[0], pair[1]);
        assertThat(excl).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyCompositeExclusionBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double excl = ConceptualExclusion.compositeExclusion(pair[0], pair[1]);
        assertThat(excl).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyCompositeExclusionReflexive(@ForAll("trajectories") long[] a) {
        double excl = ConceptualExclusion.compositeExclusion(a, a);
        // Identical → should be low (close to 0)
        assertThat(excl).isLessThan(0.2);
    }

    @Property(tries = 50)
    void propertyCompositeExclusionSeparatedDistributions(@ForAll("anySeed") int seed) {
        Random rng = new Random(seed);
        long[] a = new long[16];
        long[] b = new long[16];
        // Cluster A near 0, B near 1000
        for (int i = 0; i < 16; i++) {
            a[i] = rng.nextInt(100);
            b[i] = 1000 + rng.nextInt(100);
        }
        double excl = ConceptualExclusion.compositeExclusion(a, b);
        // Well-separated → high exclusion
        assertThat(excl).isGreaterThan(0.3);
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
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
