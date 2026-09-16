package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W204 — CognitiveEmbedding property-based tests.
 */
class CognitiveEmbeddingPropertyTest {

    @Property(tries = 30)
    void propertyEmbedReturnsCorrectDimension(@ForAll("anySeed") int seed,
                                                @ForAll("anyDimension") int dim) {
        if (dim < 1) return;
        CognitiveEmbedding emb = new CognitiveEmbedding(dim, seed);
        CognitiveGenesisProfile p = randomProfile(new Random(seed));
        double[] v = emb.embed(p);
        assertThat(v.length).isEqualTo(dim);
    }

    @Property(tries = 30)
    void propertyCosineSymmetric(@ForAll("anyVectorDim") double[] a,
                                   @ForAll("anyVectorDim") double[] b) {
        double ab = CognitiveEmbedding.cosineSimilarity(a, b);
        double ba = CognitiveEmbedding.cosineSimilarity(b, a);
        if (!Double.isNaN(ab) && !Double.isNaN(ba)) {
            assertThat(ab).isCloseTo(ba, offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyCosineSelfIsOne(@ForAll("anyVectorDim") double[] a) {
        double c = CognitiveEmbedding.cosineSimilarity(a, a);
        if (!Double.isNaN(c)) {
            assertThat(Math.abs(c - 1.0)).isLessThan(1e-9);
        }
    }

    @Property(tries = 30)
    void propertyCosineBounded(@ForAll("anyVectorDim") double[] a,
                                 @ForAll("anyVectorDim") double[] b) {
        double c = CognitiveEmbedding.cosineSimilarity(a, b);
        if (!Double.isNaN(c)) {
            assertThat(Math.abs(c)).isLessThanOrEqualTo(1.0 + 1e-9);
        }
    }

    @Property(tries = 30)
    void propertyL2Symmetric(@ForAll("anyVectorDim") double[] a,
                               @ForAll("anyVectorDim") double[] b) {
        double ab = CognitiveEmbedding.l2Distance(a, b);
        double ba = CognitiveEmbedding.l2Distance(b, a);
        if (!Double.isNaN(ab) && !Double.isNaN(ba)) {
            assertThat(ab).isCloseTo(ba, offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyL2TriangleInequality(@ForAll("anyVectorDim") double[] a,
                                         @ForAll("anyVectorDim") double[] b,
                                         @ForAll("anyVectorDim") double[] c) {
        double ab = CognitiveEmbedding.l2Distance(a, b);
        double bc = CognitiveEmbedding.l2Distance(b, c);
        double ac = CognitiveEmbedding.l2Distance(a, c);
        if (!Double.isNaN(ab) && !Double.isNaN(bc) && !Double.isNaN(ac)) {
            assertThat(ac).isLessThanOrEqualTo(ab + bc + 1e-9);
        }
    }

    @Property(tries = 30)
    void propertyNearestNeighborsValid(@ForAll("anySeed") int seed,
                                          @ForAll("anyDimension") int dim,
                                          @ForAll("anyK") int k) {
        if (dim < 1 || k < 1) return;
        CognitiveEmbedding emb = new CognitiveEmbedding(dim, seed);
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 16; i++) profiles.add(randomProfile(rng));
        int[] nn = emb.nearestNeighbors(randomProfile(rng), profiles, k);
        int expectedLen = Math.min(k, profiles.size());
        assertThat(nn.length).isEqualTo(expectedLen);
        for (int idx : nn) {
            assertThat(idx).isBetween(0, profiles.size() - 1);
        }
    }

    @Property(tries = 30)
    void propertySameSeedDeterministic(@ForAll("anySeed") int seed,
                                          @ForAll("anyDimension") int dim) {
        if (dim < 1) return;
        CognitiveEmbedding emb1 = new CognitiveEmbedding(dim, seed);
        CognitiveEmbedding emb2 = new CognitiveEmbedding(dim, seed);
        Random rng = new Random(seed);
        CognitiveGenesisProfile p = randomProfile(rng);
        double[] v1 = emb1.embed(p);
        double[] v2 = emb2.embed(p);
        for (int i = 0; i < v1.length; i++) {
            assertThat(v1[i]).isCloseTo(v2[i], offset(1e-9));
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyDimension() {
        return Arbitraries.integers().between(8, 256);
    }

    @Provide
    Arbitrary<Integer> anyK() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<double[]> anyVectorDim() {
        return Arbitraries.integers().between(4, 32).flatMap(dim ->
            Arbitraries.doubles().between(-1.0, 1.0).array(double[].class).ofSize(dim)
        );
    }

    private static CognitiveGenesisProfile randomProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
            rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
