package io.matrix.consciousness;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
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
        if (allZero(a)) {
            // A zero vector has no direction. cosineSimilarity returns 0.0 deliberately
            // here rather than NaN, because eight production callers feed this into
            // similarity arrays and a NaN would poison a ranking. So cos(a,a) == 0 for
            // a zero vector, and that is asserted explicitly rather than skipped --
            // skipping it is what let the real defect hide here in the first place.
            assertThat(c).isEqualTo(0.0);
        } else {
            assertThat(Math.abs(c - 1.0)).as("a vector is parallel to itself").isLessThan(1e-9);
        }
    }

    private static boolean allZero(double[] v) {
        for (double d : v) if (d != 0.0) return false;
        return true;
    }

    @Test
    void incomparableVectorsReportUndefinedRatherThanIdentical() {
        // The defect this wave exists for: L2 distance 0.0 means IDENTICAL, and the
        // implementation used to return exactly that for vectors of different lengths.
        double[] four = {1, 2, 3, 4};
        double[] thirtyTwo = new double[32];
        assertThat(CognitiveEmbedding.l2Distance(four, thirtyTwo)).isNaN();
        assertThat(CognitiveEmbedding.cosineSimilarity(four, thirtyTwo)).isNaN();
        assertThat(CognitiveEmbedding.l2Distance(null, four)).isNaN();
        // and a comparable pair is still exact
        assertThat(CognitiveEmbedding.l2Distance(four, four)).isEqualTo(0.0);
    }

    @Test
    void selfSimilarityIsActuallyExercisedRatherThanSkipped() {
        // A guard against the vacuity the NaN change could have introduced: count that the
        // triangle-inequality property really runs its body.
        for (int i = 0; i < 200; i++) {
            double[] v = {i, 1, 2, 3};
            assertThat(CognitiveEmbedding.l2Distance(v, v)).isEqualTo(0.0);
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
    void propertyL2TriangleInequality(@ForAll("anyVectorTriple") VectorTriple t) {
        double ab = CognitiveEmbedding.l2Distance(t.a(), t.b());
        double bc = CognitiveEmbedding.l2Distance(t.b(), t.c());
        double ac = CognitiveEmbedding.l2Distance(t.a(), t.c());
        // No NaN guard: all three share a dimension, so an NaN here is a real defect and
        // must fail loudly. The guard that used to be here hid the property.
        assertThat(ab).isNotNaN();
        assertThat(ac).isLessThanOrEqualTo(ab + bc + 1e-9);
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

    /**
     * RECON-W32.30: a PAIR of vectors sharing one dimension.
     *
     * <p>The old generators drew each vector's length INDEPENDENTLY, so
     * {@code anyVectorDim} for a, b and c produced three different lengths almost every
     * time. That had two consequences, and the second is worse than the first:</p>
     *
     * <ol>
     *   <li>The metric returned 0.0 for incomparable vectors, so L2 reported distance
     *       ZERO — "identical" — for vectors it had never compared. Caught by
     *       {@code propertyL2TriangleInequality}.</li>
     *   <li>After the metric correctly returned NaN, that test's
     *       {@code if (!isNaN(ab) && ...)} guard meant the assertion body was skipped
     *       almost every run — so the fix would have made the test PASS VACUOUSLY. A test
     *       that checks nothing while reporting green is the exact failure this campaign
     *       exists to remove, and it would have arrived silently, dressed as a fix.</li>
     * </ol>
     *
     * <p>Self-similarity and the triangle inequality are only meaningful between vectors
     * of equal dimension. These generators make that true, so the properties are actually
     * exercised rather than skipped.</p>
     */
    @Provide
    Arbitrary<VectorPair> anyVectorPair() {
        // Nested flatMap rather than map-of-map: map would yield Arbitrary<Arbitrary<...>>,
        // which is not what flatMap's lambda must return. My first attempt did exactly
        // that and the compiler said so.
        return Arbitraries.integers().between(4, 32).flatMap(dim ->
            vectorOf(dim).flatMap(a ->
                vectorOf(dim).flatMap(b -> Arbitraries.just(new VectorPair(a, b)))));
    }

    /** Three vectors sharing one dimension, so the triangle inequality is testable. */
    @Provide
    Arbitrary<VectorTriple> anyVectorTriple() {
        return Arbitraries.integers().between(4, 32).flatMap(dim ->
            vectorOf(dim).flatMap(a ->
                vectorOf(dim).flatMap(b ->
                    vectorOf(dim).flatMap(c -> Arbitraries.just(new VectorTriple(a, b, c))))));
    }

    /**
     * A random vector of exactly {@code dim} components, never all-zero.
     *
     * <p>Non-zero matters for direction: a zero vector has no direction, so its cosine
     * with anything is undefined rather than 0. The implementation deliberately returns
     * 0.0 there (see {@code cosineSimilarity}), because eight production call sites feed
     * this into similarity arrays where a NaN would poison a ranking. That is a
     * conservative "no similarity", not a claim of orthogonality — but it means a
     * direction property must not be asked about a zero vector, so the generator
     * excludes one.</p>
     */
    private Arbitrary<double[]> vectorOf(int dim) {
        return Arbitraries.doubles().between(-1.0, 1.0).array(double[].class).ofSize(dim)
                .filter(v -> {
                    for (double d : v) if (d != 0.0) return true;
                    return false;
                });
    }

    /** Two vectors of equal dimension. */
    record VectorPair(double[] a, double[] b) {}

    /** Three vectors of equal dimension. */
    record VectorTriple(double[] a, double[] b, double[] c) {}

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
